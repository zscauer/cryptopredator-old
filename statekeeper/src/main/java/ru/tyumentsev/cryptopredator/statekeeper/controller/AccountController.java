package ru.tyumentsev.cryptopredator.statekeeper.controller;

import com.binance.api.client.domain.account.AssetBalance;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.statekeeper.service.AccountService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class AccountController {

    AccountService accountService;

    @GetMapping("/accountBalance")
    public List<AssetBalance> getAccountBalance() {
        return accountService.getAccountBalances();
    }

    @GetMapping("/accountBalance/{asset}")
    public AssetBalance getAssetBalance(@PathVariable String asset) {
        return accountService.getAccountBalances().stream()
                .filter(assetBalance -> assetBalance.getAsset().equalsIgnoreCase(asset))
                .findFirst().orElseThrow();
    }

    @GetMapping("/accountBalance/{asset}/free")
    public Float getFreeAssetBalance(@PathVariable String asset) {
        return accountService.getFreeAssetBalance(asset);
    }

}
