package com.ganten.peanuts.raftpoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;

/**
 * 共识提交后更新内存 KV，并通知 {@link DownstreamSink}（Kafka 等）。
 * <p>
 * 命令格式：{@code PUT|key|value}（UTF-8）。
 */
public class DemoStateMachine extends StateMachineAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DemoStateMachine.class);
    private static final String PREFIX = "PUT|";

    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();
    private final DownstreamSink downstream;

    public DemoStateMachine(DownstreamSink downstream) {
        this.downstream = downstream;
    }

    public ConcurrentMap<String, String> getStoreView() {
        return store;
    }

    @Override
    public void onApply(Iterator iter) {
        while (iter.hasNext()) {
            long index = iter.getIndex();
            long term = iter.getTerm();
            ByteBuffer data = iter.getData();
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                applyCommand(bytes);
                downstream.onCommitted(index, term, bytes);
            } catch (RuntimeException ex) {
                LOG.error("apply failed at index={}", index, ex);
            }
            iter.next();
        }
    }

    private void applyCommand(byte[] bytes) {
        String line = new String(bytes, StandardCharsets.UTF_8);
        if (!line.startsWith(PREFIX)) {
            return;
        }
        String rest = line.substring(PREFIX.length());
        int p = rest.indexOf('|');
        if (p <= 0) {
            return;
        }
        String key = rest.substring(0, p);
        String value = rest.substring(p + 1);
        store.put(key, value);
        LOG.info("[fsm] apply PUT key={} (storeSize={})", key, store.size());
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        File file = new File(writer.getPath() + File.separator + "kv.snapshot");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            for (ConcurrentMap.Entry<String, String> e : store.entrySet()) {
                pw.println(escape(e.getKey()) + "=" + escape(e.getValue()));
            }
            pw.flush();
            if (!writer.addFile("kv.snapshot")) {
                done.run(new Status(RaftError.EIO, "addFile failed"));
                return;
            }
            done.run(Status.OK());
        } catch (IOException e) {
            done.run(new Status(RaftError.EIO, "snapshot save: %s", e.getMessage()));
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') {
                    sb.append('\n');
                } else if (n == '=' || n == '\\') {
                    sb.append(n);
                } else {
                    sb.append(c).append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        String path = reader.getPath() + File.separator + "kv.snapshot";
        if (reader.getFileMeta("kv.snapshot") == null) {
            LOG.warn("snapshot file missing, path={}", path);
            return false;
        }
        store.clear();
        File file = new File(path);
        if (!file.isFile()) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = unescape(line.substring(0, eq));
                String v = unescape(line.substring(eq + 1));
                store.put(k, v);
            }
            return true;
        } catch (IOException e) {
            LOG.error("snapshot load failed", e);
            return false;
        }
    }

    @Override
    public void onError(RaftException e) {
        LOG.error("Raft error", e);
    }
}
