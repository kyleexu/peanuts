package com.ganten.peanuts.protocol.raft;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.ganten.peanuts.protocol.codec.AbstractCodec;

/**
 * 通用状态机：用 codec 解码日志，再交给业务 handler 处理。
 *
 * <p>这里的 {@link #onApply(Iterator)} 只会收到已经 committed 的日志条目（即满足 Raft 提交条件后）。</p>
 * <p>如果条目来自本地提案，{@code iter.done()} 通常非空；如果来自复制回放，通常为 null。</p>
 */
public class CodecRaftStateMachine<M, C extends AbstractCodec<M>> extends StateMachineAdapter {

    private final C codec;
    private final RaftMessageApplyHandler<M> applyHandler;

    public CodecRaftStateMachine(C codec, RaftMessageApplyHandler<M> applyHandler) {
        this.codec = codec;
        this.applyHandler = applyHandler;
    }


    @Override
    public void onApply(Iterator iter) {
        /**
         * 当到达多数同意之后（也就是 raft log commit），任务会被加入到这个迭代中。
         */
        while (iter.hasNext()) {
            ByteBuffer data = iter.getData();
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            M message = codec.decode(new UnsafeBuffer(bytes), 0);

            // done 仅在本地提案路径可用：业务 handler 负责决定何时回调 done.run(Status)。
            Closure done = iter.done();
            applyHandler.onCommitted(message, done != null, done);
            iter.next();
        }
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        // Current state is fully derived from committed logs, so no custom snapshot files are required.
        // Return success to avoid periodic ERROR logs from the default adapter implementation.
        if (done != null) {
            done.run(Status.OK());
        }
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        // No custom snapshot payload to restore.
        return true;
    }
}
