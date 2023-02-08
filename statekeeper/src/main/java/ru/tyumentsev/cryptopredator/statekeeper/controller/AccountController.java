package ru.tyumentsev.cryptopredator.statekeeper.controller;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
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

import java.util.Comparator;
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
    BinanceApiRestClient restClient;

    @GetMapping("/accountBalance")
    public List<AssetBalance> accountBalance() {
        Account account = restClient.getAccount();
        return account.getBalances().stream()
                .filter(balance -> Float.parseFloat(balance.getFree()) > 0
                        || Float.parseFloat(balance.getLocked()) > 0)
                .sorted(Comparator.comparing(AssetBalance::getAsset))
                .toList();
    }

    @GetMapping("/accountBalance/{ticker}")
    public AssetBalance assetBalance(@PathVariable String ticker) {
        Account account = restClient.getAccount();
        return account.getAssetBalance(ticker.toUpperCase());
    }

    @GetMapping("/activeStrategies")
    public Map<String, String> getActiveStrategies() {
        return accountService.getActiveBots();
    }

    @PostMapping("/activeStrategies")
    public void addActiveStrategy(@RequestBody Map<String, String> parameters) {
        accountService.getActiveBots().put(parameters.get("strategyName"), parameters.get("botAddress"));
        log.info("Strategy {} was added to user data stream monitoring with address {}.", parameters.get("strategyName"), parameters.get("botAddress"));
    }

    @DeleteMapping("/activeStrategies")
    public void deleteActiveStrategy(@RequestParam String strategyName) {
        accountService.getActiveBots().remove(strategyName);
        log.info("Strategy {} was deleted from user data stream monitoring.", strategyName);
    }
}
