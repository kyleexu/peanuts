package com.ganten.peanuts.account.controller;

import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ganten.peanuts.account.model.BalanceOperationRequest;
import com.ganten.peanuts.account.model.OperationResult;
import com.ganten.peanuts.account.model.TransferIncreaseRequest;
import com.ganten.peanuts.account.model.TransferResult;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.entity.AccountAssetSnapshot;
import com.ganten.peanuts.common.enums.Currency;

/**
 * 账户余额接口。
 *
 * 该控制器提供按用户和币种维度的余额查询与变更能力，包括可用余额增加、锁定、扣减以及锁定转可用。
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 查询用户在指定币种下的余额快照。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @return 包含可用余额和锁定余额的快照
     */
    @GetMapping("/{userId}/currencies/{currency}")
    public ResponseEntity<AccountAssetSnapshot> query(@PathVariable long userId, @PathVariable Currency currency) {
        return ResponseEntity.ok(accountService.query(userId, currency));
    }

    /**
     * 增加用户指定币种的可用余额。
     *
     * @param request 余额操作请求（用户、币种、金额）
     * @return 增加后的余额快照
     */
    @PostMapping("/increase")
    public ResponseEntity<AccountAssetSnapshot> increase(@Valid @RequestBody BalanceOperationRequest request) {
        accountService.increaseAvailable(request.getUserId(), request.getCurrency(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getCurrency());
        return ResponseEntity.ok(snapshot);
    }

    /**
     * 将可用余额锁定到锁定余额。
     *
     * @param request 余额操作请求（用户、币种、金额）
     * @return 操作结果及当前余额快照
     */
    @PostMapping("/lock")
    public ResponseEntity<OperationResult> lock(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success = accountService.tryLock(request.getUserId(), request.getCurrency(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getCurrency());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    /**
     * 扣减可用余额。
     *
     * @param request 余额操作请求（用户、币种、金额）
     * @return 操作结果及当前余额快照
     */
    @PostMapping("/deduct/available")
    public ResponseEntity<OperationResult> deductAvailable(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success =
                accountService.deductAvailable(request.getUserId(), request.getCurrency(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getCurrency());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    /**
     * 扣减锁定余额。
     *
     * @param request 余额操作请求（用户、币种、金额）
     * @return 操作结果及当前余额快照
     */
    @PostMapping("/deduct/locked")
    public ResponseEntity<OperationResult> deductLocked(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success = accountService.deductLocked(request.getUserId(), request.getCurrency(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getCurrency());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    /**
     * 将转出方的锁定余额转入转入方的可用余额。
     *
     * @param request 转账请求（转出方、转入方、币种、金额）
     * @return 转账结果以及双方最新余额快照
     */
    @PostMapping("/transfer-increase")
    public ResponseEntity<TransferResult> transferIncrease(@Valid @RequestBody TransferIncreaseRequest request) {
        boolean success = accountService.transferIncrease(request.getFromUserId(), request.getToUserId(),
                request.getCurrency(), request.getAmount());

        TransferResult response = new TransferResult();
        response.setSuccess(success);
        response.setFrom(accountService.query(request.getFromUserId(), request.getCurrency()));
        response.setTo(accountService.query(request.getToUserId(), request.getCurrency()));
        return ResponseEntity.ok(response);
    }
}
