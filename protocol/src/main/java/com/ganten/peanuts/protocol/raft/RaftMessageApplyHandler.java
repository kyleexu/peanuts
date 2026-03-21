package com.ganten.peanuts.protocol.raft;

import com.alipay.sofa.jraft.Closure;

/**
 * 状态机已提交消息回调。
 *
 * @param <M> 业务消息类型
 */
public interface RaftMessageApplyHandler<M> {

    /**
     * @param message 提交后的业务消息
     * @param localApply 是否本地提案（iter.done()!=null）
     * @param done 可选回调；非本地提案时可能为 null
     */
    void onCommitted(M message, boolean localApply, Closure done);
}
