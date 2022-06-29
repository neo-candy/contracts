package io.neocandy.tokens.nep11.lollipopnft;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

public class TestHelper {

    /* METHOD NAMES */
    // NFT CONTRACT
    protected static final String PROPERTIES = "properties";
    protected static final String JSON_PROPERTIES = "propertiesJson";
    protected static final String UPDATE_PAUSE = "updatePause";
    protected static final String ROYALTIES = "getRoyalties";
    protected static final String TOKENS_OF = "tokensOf";
    protected static final String OWNER_OF = "ownerOf";
    protected static final String TOKENS_OF_JSON = "tokensOfJson";
    protected static final String CURRENT_PRICE = "currentPrice";
    protected static final String CURRENT_SUPPLY = "currentSupply";
    protected static final String IS_PAUSED = "isPaused";
    protected static final String ADMIN_MINT = "adminMint";

    protected static final Logger log = LoggerFactory.getLogger(TestHelper.class);

    protected static void transfer17(
            FungibleToken token, Account from, Hash160 to, BigInteger amount, ContractParameter data, Neow3j neow3j,
            boolean shouldLog)
            throws Throwable {
        Transaction tx = token.transfer(
                from, to, amount, data).sign();
        NeoSendRawTransaction res = tx.send();
        if (shouldLog) {
            log.info("gas fee transfer17: {}\n", tx.getSystemFee() + tx.getNetworkFee());
        }
        if (res.hasError()) {
            throw new Exception(res.getError().getMessage());
        }
        Await.waitUntilTransactionIsExecuted(res.getSendRawTransaction().getHash(), neow3j);
        if (shouldLog) {
            log.info("{} transfer success: {}\n", token.getSymbol(), tx.getApplicationLog().getExecutions().get(0));
        }
    }

    protected static void transfer11(
            SmartContract token, byte[] tokenId, Account from, Hash160 to, Neow3j neow3j) throws Throwable {
        Transaction tx = null;

        tx = token.invokeFunction("transfer", new ContractParameter[] { ContractParameter.hash160(to),
                ContractParameter.byteArray(tokenId), null })
                .signers(new Signer[] { AccountSigner.calledByEntry(from) }).sign();

        log.info("gas fee transfer11: {}\n", tx.getSystemFee() + tx.getNetworkFee());
        NeoSendRawTransaction res = tx.send();
        if (res.hasError()) {
            throw new Exception(res.getError().getMessage());
        }
        Await.waitUntilTransactionIsExecuted(res.getSendRawTransaction().getHash(), neow3j);
        log.info("transfer11 success: {}\n", tx.getApplicationLog().getExecutions().get(0));

    }

    protected static List<StackItem> invokeWrite(
            SmartContract contract, String method, List<ContractParameter> params, Account signer, Neow3j neow3j)
            throws Throwable {
        Transaction tx = null;

        tx = contract.invokeFunction(method, params.toArray(new ContractParameter[0]))
                .signers(new Signer[] { AccountSigner.calledByEntry(signer) }).sign();

        log.info("gas fee invokeWrite:{} {}\n", method, tx.getSystemFee() + tx.getNetworkFee());
        NeoSendRawTransaction res = tx.send();
        if (res.hasError()) {
            log.info("error on tx.send {} : {}\n", method, res.getError().getMessage());
            throw new Exception(res.getError().getMessage());
        }
        log.info("invoke {} success, waiting for response...\n", method);
        Await.waitUntilTransactionIsExecuted(res.getSendRawTransaction().getHash(), neow3j);
        log.info("application log of {} : {}\n", method, tx.getApplicationLog());
        return tx.getApplicationLog().getExecutions().get(0).getStack();
    }

    protected static void mintNFT(FungibleToken token, Account account, BigInteger amount, SmartContract nft,
            Neow3j neow3j, boolean log) throws Throwable {
        transfer17(token, account, nft.getScriptHash(), amount, null, neow3j, log);
    }

}
