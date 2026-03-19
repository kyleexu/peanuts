package com.ganten.peanuts.account.controller;

import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ganten.peanuts.account.cache.AccountCache.AccountAssetSnapshot;
import com.ganten.peanuts.account.model.BalanceOperationRequest;
import com.ganten.peanuts.account.model.OperationResult;
import com.ganten.peanuts.account.model.TransferIncreaseRequest;
import com.ganten.peanuts.account.model.TransferResult;
import com.ganten.peanuts.account.service.AccountService;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{userId}/assets/{asset}")
    public ResponseEntity<AccountAssetSnapshot> query(@PathVariable long userId, @PathVariable String asset) {
        return ResponseEntity.ok(accountService.query(userId, asset));
    }

    @PostMapping("/increase")
    public ResponseEntity<AccountAssetSnapshot> increase(@Valid @RequestBody BalanceOperationRequest request) {
        accountService.increaseAvailable(request.getUserId(), request.getAsset(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getAsset());
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/lock")
    public ResponseEntity<OperationResult> lock(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success = accountService.lock(request.getUserId(), request.getAsset(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getAsset());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    @PostMapping("/deduct/available")
    public ResponseEntity<OperationResult> deductAvailable(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success = accountService.deductAvailable(request.getUserId(), request.getAsset(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getAsset());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    @PostMapping("/deduct/locked")
    public ResponseEntity<OperationResult> deductLocked(@Valid @RequestBody BalanceOperationRequest request) {
        boolean success = accountService.deductLocked(request.getUserId(), request.getAsset(), request.getAmount());
        AccountAssetSnapshot snapshot = accountService.query(request.getUserId(), request.getAsset());
        return ResponseEntity.ok(new OperationResult(success, snapshot));
    }

    @PostMapping("/transfer-increase")
    public ResponseEntity<TransferResult> transferIncrease(@Valid @RequestBody TransferIncreaseRequest request) {
        boolean success = accountService.transferIncrease(request.getFromUserId(), request.getToUserId(),
            request.getAsset(), request.getAmount());

        TransferResult response = new TransferResult();
        response.setSuccess(success);
        response.setFrom(accountService.query(request.getFromUserId(), request.getAsset()));
        response.setTo(accountService.query(request.getToUserId(), request.getAsset()));
        return ResponseEntity.ok(response);
    }
}
