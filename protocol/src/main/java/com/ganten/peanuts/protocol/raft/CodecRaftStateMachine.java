package com.ganten.peanuts.protocol.raft;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.ganten.peanuts.protocol.codec.AbstractCodec;

/**
 * 通用状态机：用 codec 解码日志，再交给业务 handler 处理。
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
        while (iter.hasNext()) {
            ByteBuffer data = iter.getData();
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            M message = codec.decode(new UnsafeBuffer(bytes), 0);
            Closure done = iter.done();
            boolean localApply = (done != null);
            applyHandler.onCommitted(message, localApply, done);
            iter.next();
        }
    }
}
