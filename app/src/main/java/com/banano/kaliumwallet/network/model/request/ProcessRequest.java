package com.banano.kaliumwallet.network.model.request;

import com.banano.kaliumwallet.network.model.Actions;
import com.banano.kaliumwallet.network.model.BaseRequest;
import com.google.gson.annotations.SerializedName;

/**
 * Subscribe to websocket server for updates regarding the specified account.
 * First action to take when connecting when app opens or reconnects, IF a wallet already exists
 */

public class ProcessRequest extends BaseRequest {
    @SerializedName("action")
    private String action;

    @SerializedName("block")
    private String block;

    @SerializedName("do_work")
    private boolean doWork;

    public ProcessRequest() {
        this.action = Actions.PROCESS.toString();
    }

    public ProcessRequest(String block, boolean doWork) {
        this.action = Actions.PROCESS.toString();
        this.block = block;
        this.doWork = doWork;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public void setDoWork(boolean doWork) {
        this.doWork = doWork;
    }

    public boolean getDoWork() {
        return doWork;
    }

    @Override
    public String toString() {
        return "ProcessRequest{" +
                "action='" + action + '\'' +
                ", block='" + block + '\'' +
                ", doWork='" + doWork + '\'' +
                '}';
    }
}
