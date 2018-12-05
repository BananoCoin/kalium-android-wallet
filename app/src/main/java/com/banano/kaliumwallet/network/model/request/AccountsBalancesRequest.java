package com.banano.kaliumwallet.network.model.request;

import com.banano.kaliumwallet.network.model.Actions;
import com.banano.kaliumwallet.network.model.BaseRequest;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AccountsBalancesRequest extends BaseRequest {
    @SerializedName("action")
    private String action;

    @SerializedName("accounts")
    private List<String> accounts;


    public AccountsBalancesRequest(List<String> accounts) {
        this.action = Actions.BALANCES.toString();
        this.accounts = accounts;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<String> accounts) {
        this.accounts = accounts;
    }
}
