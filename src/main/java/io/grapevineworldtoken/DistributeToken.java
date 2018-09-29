package io.grapevineworldtoken;

import io.grapevineworldtoken.contract.generated.GrapevineToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.lang.System.*;

/**
 * DistributeToken - A class used to distribute the tokens for the investors
 * and the airdrop.
 * <p>
 * The flow is defined as follows.
 * <ul>
 * <li>Get test ether from ropsten</li>
 * <li>Generate a Wallet which will have tokens and the test ethers</li>
 * <li>Obtain a CSV with ETH address -> token of beneficiaries</li>
 *
 * <li>For each beneficiary: transfer token, and wait some seconds for the token distribution</li>
 * <li>Make sure that the token has been succesfully transferred by checking the balance of the
 * wallet, and the balance of the beneficiary, making sure that it is the same</li>
 * <li>Write in a file the addresses successfully processed</li>
 * </ul>
 * <p>
 * <br/><br/>
 * After the succesfull implementation, the switch will be made on the mainnet on infura.
 * <br/><br/>
 * Ideally, the transactions are batched 100 by 100.
 *
 * @author max
 */
public class DistributeToken {

    /**
     * Address of the token contract in RINKEBY.
     */
    private final static String RINKEBY_CONTRACT_ADDRESS = "0x62c3020666b1be5fb92c2b24ec6d09c6855293e1";

    /**
     * Address of the TEST token contract in the main net
     */
    private final static String MAINNET_TEST_CONTRACT_ADDRESS = "0x0c31f4ac8f7d6958e9226b4faf5dd7f235b9d552";

    /**
     * The URL for infura
     */
    private final static String MAINNET_INFURA_ADDRESS = "https://mainnet.infura.io";

    private static BigInteger nonce;
    /**
     * The URL for infura in the testnet
     */
    private final static String TESTNET_INFURA = "https://ropsten.infura.io";


    /**
     * The HTTP Client to the blockchain.
     */
    private Web3j web3;

    /**
     * Pointer to the smart contract
     */
    private GrapevineToken gt;

    /** The address of the personal wallet which will hold all the tokens and the Ether necessary to pay the gas. */
    private String myAddress;

    /** The credentials to sign transactions. */
    private Credentials credentials;


    /** Return the contract. */
    public GrapevineToken getContract() {
        return gt;
    }

    /** Return the HTTP pointer to the blockchain. */
    private Web3j getWeb3() {
        return web3;
    }


    /**
     * Constructor for the DistributeToken class.
     */
    public DistributeToken() {
        System.out.println("Constructor");
    }

    /**
     * Main class entry method. It performs the following:
     * <ul>
     *     <li>Load the wallet credentials</li>
     *     <li>Checks if the balance of ether and GVINE is greater than 0 (It is not checking if it is enough, i.e., it does do any estimation)</li>
     *     <li>Reads the file of addresses to be processed, and the file of addresses eventually processed already</li>
     *     <li>Merges the two, continuing processing only the subset left</li>
     *     <li>Update the processed field with the information: recipient_address;isCorrect:timestamp:(block_number/cumulative_gas/gas_used/transaction_has)</li>
     * </ul>
     * @param args the program arguments (the password for the credentials)
     * @param web3j the HTTP handler for the blockchain
     * @throws IOException for I/O issues with the blockchain (infura) communication
     * @throws CipherException for signing issues
     */
    public void process(String[] args, Web3j web3j) throws IOException, CipherException {

        this.web3 = web3j;

        loadWallet(args);

        EthGetBalance balance = getMyBalanceInEther();



        //TransactionReceipt rec = gt.transfer(wi.getAddress(), BigInteger.valueOf(wi.getAmount())).send();

        try {
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                    myAddress, DefaultBlockParameterName.LATEST).sendAsync().get();

            nonce = ethGetTransactionCount.getTransactionCount();
            System.out.print(" obtained nonce IN PROCESS " + nonce);
        } catch (Throwable e) {
            throw new IOException(e);
        }



        loadGrapevineTokenContract();

        BigInteger gvwbalance = getActualGVINEBalance();

        // Now make sure that I have Ether and tokens to perform the
        // token distribution - Here just check if they are greater than 0

        checkBalancesAreEnough(balance, gvwbalance);

        System.out.println("Balances are ok - Now reading the CSV file");

        // Ok, now I've two files: one is the bounty, the other is the one already
        // processed. I read the file (Bounty.csv) and I read it all in memory.
        // I flag the one processed, and I store it in the processed.csv

        List<String> addresses = Files.readAllLines(new File("/Users/FFJB/git/GVWDistributeToken/src/main/java/io/grapevineworldtoken/test.csv").toPath());

        System.out.println("I have " + addresses.size() + " address to process in the bounty.csv");

        // It's concurrent in case we want to run a parallelStream().

        ConcurrentMap<String, WorkItem> list = addresses.stream().collect(Collectors.toConcurrentMap(
                DistributeToken::extractMethod,
                DistributeToken::makeWorkItem));

        System.out.println("The from bounty.csv is: " + list.size());

        ConcurrentHashMap<String, WorkItem> processedList = new ConcurrentHashMap<>();

        // now read the ones already used.
        File alreadyProcessedFile = new File("/Users/FFJB/git/GVWDistributeToken/src/main/java/io/grapevineworldtoken/testProcessed.csv");
        if (alreadyProcessedFile.exists()) {
            System.out.println("The already processed exists");
            // now read it and put it into an hashmap.
            List<String> lines = Files.readAllLines(alreadyProcessedFile.toPath());
            System.out.println("I have processed already " + lines.size() + " addresses");

            // Here a line is as follows
            // address;amount;flagDone;when;transactionHash
            processedList = (ConcurrentHashMap<String, WorkItem>) lines.stream().collect(
                    Collectors.toConcurrentMap(DistributeToken::extractMethod,
                            DistributeToken::makeWorkItem));
            System.out.println("Processed list is " + processedList.size());
        }


        // Now start to process only the left ones.
        final ConcurrentHashMap<String, WorkItem> processedListLambda = processedList;
        System.out.println("Processed list lambda is " + processedListLambda.size());
        BufferedWriter writer = Files.newBufferedWriter(alreadyProcessedFile.toPath(), StandardOpenOption.APPEND);

        list.entrySet().stream().forEach(stringWorkItemEntry -> {

            // Check if a workitem is in the hashmap
            processedListLambda.computeIfAbsent(stringWorkItemEntry.getKey(), s -> {
                System.out.print("Operating on " + s);
                WorkItem wi = stringWorkItemEntry.getValue();
                try {
                    WorkItem winew = processWorkItem(wi);

                    // I need to update the list of processed
                    writer.append(winew.toString());

                    writer.flush();
                    System.out.println();
                    return winew;

                } catch (Exception e) {
                    System.err.println(" File not updated for address " + wi);
                    throw new IllegalStateException(e);
                }
            });
        });

        writer.close();
        ;
    }


    /**
     * Check if the balances are greater than zero. This method is not doing
     * any estimation of amount needed.
     *
     * @param balance Balances in ether
     * @param gvwbalance Balances in GVINE
     */
    private void checkBalancesAreEnough(EthGetBalance balance, BigInteger gvwbalance) {
        if (!(balance.getBalance().longValue() >= 0L)) {
            throw new IllegalStateException("I have no balance to use");
        }

        if (!(gvwbalance.doubleValue() >= 0)) {
            throw new IllegalStateException("I have no GVW that I can send");
        }
    }

    /**
     * Get the actual GVINE balance for the owner's wallet.
     * @return The GVINE balance
     * @throws IOException for HTTP errors talking to the blockchain
     */
    private BigInteger getActualGVINEBalance() throws IOException {
        BigInteger gvwbalance;
        try {
            gvwbalance = gt.balanceOf(myAddress).send();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Unable to read the balance: " + e.getMessage());

        }
        System.out.println("Getting the token balance for my address " + myAddress + ": " + gvwbalance.doubleValue());
        return gvwbalance;
    }

    /**
     * Loads the GVINE contract.
     *
     * @throws IOException for errors talking to the blockchain
     */
    private void loadGrapevineTokenContract() throws IOException {
        System.out.println("Loading the GVW Token");

        gt = GrapevineToken.load(MAINNET_TEST_CONTRACT_ADDRESS, web3, credentials, DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT);

        System.out.println("Loaded the contract: " + gt.getContractAddress());

        try {
            System.out.println("Total supply: " + gt.totalSupply().send());
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Unable to read the total supply: " + e.getMessage());
        }
    }

    /**
     * Get the balance of ether in wei
     * @return the ether balance for the owner's wallet
     * @throws IOException In case of HTTP errors talking to the blockchain
     */
    private EthGetBalance getMyBalanceInEther() throws IOException {
        EthGetBalance balance = web3.ethGetBalance(myAddress, DefaultBlockParameterName.LATEST).send();

        if (balance != null || !balance.hasError()) {
            out.println(new StringBuilder().append("My balance is: ").append(Convert.fromWei(
                    balance.getBalance().toString(), Convert.Unit.ETHER)).append(" Ether").toString());

        } else {
            err.println("Error getting the balance, " + balance.getError().getMessage());
            System.exit(-2);
        }
        return balance;
    }

    /**
     * Connect to the blockchain node (i.e., infura).
     *
     * @return the connection handler
     * @throws IOException if the connection can't be established
     */
    private static Web3j connectToNetwork() throws IOException {
        out.println("Connecting to the network");
        Web3j web3 = Web3j.build(new HttpService(MAINNET_INFURA_ADDRESS));
        Web3ClientVersion web3ClientVersion = web3.web3ClientVersion().send();
        out.println(new StringBuilder().append("Infura version ").append(web3ClientVersion.getWeb3ClientVersion()).toString());
        return web3;
    }

    /**
     * Load the wallet which will transfer tokens from.
     * @param args The password
     * @throws IOException if the file does not exist or is not readable
     * @throws CipherException if the password is wrong
     */
    private void loadWallet(String[] args) throws IOException, CipherException {
        out.println("Loading the wallet");

        credentials =
                WalletUtils.loadCredentials("eheheheh, mistake",
                        "/Users/FFJB/git/GVWDistributeToken/src/main/resources/UTC--2018-09-20T12-00-55.311Z--52c09cf088e672cd457b971477b3f21e874087e4.json");
        myAddress = credentials.getAddress();
        out.println("My address is: " + myAddress);
    }

    /**
     * Encoding of the function transfer to send the Raw transaction to the
     * infura.
     * @param to the recipient of the GVINE
     * @param value the amount of GVINE
     * @return the encoded function
     */
    private org.web3j.abi.datatypes.Function transfer(String to, BigInteger value) {
        return new Function(
                "transfer",
                Arrays.asList(new Address(to), new Uint256(value)),
                Collections.singletonList(new TypeReference<Bool>() {
                }));
    }


    /**
     * Perform the real transfer. For each work item (i.e., one address),
     * get the gas price, sign the transaction, and wait for the transaction receipt.
     * Notably, this is done to avoid flooding the main net, to wait for a proper nonce,
     * and to make sure that the transaction has been executed. The polling happens
     * every two seconds.
     *
     * @param wi the work item
     * @return the updated work item
     * @throws Exception for malfunctioning of the ethereum client
     */
    private WorkItem processWorkItem(WorkItem wi) throws Exception {
        // Now I transer the tokens
        Web3j web3j = getWeb3();
        GrapevineToken gt = getContract();





        EthGasPrice ethgasPrice = web3j.ethGasPrice().send();
        BigInteger gasPrice = null;

        if (ethgasPrice.hasError() == true) {
            gasPrice = BigInteger.ZERO;
        } else {
            gasPrice = ethgasPrice.getGasPrice().add(BigInteger.valueOf(1000000000L));
        }

        System.out.print(" generating transaction (gas price: " + gasPrice.longValue() + ")");

        try {
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                    myAddress, DefaultBlockParameterName.LATEST).sendAsync().get();

            nonce = ethGetTransactionCount.getTransactionCount();
        } catch (Throwable e) {
            throw new IOException(e);
        }
//        nonce.add(BigInteger.ONE);
        System.out.println("Actual nonce is: " + nonce);
        Function function = transfer(wi.getAddress(), wi.getAmount());
        String encodedMethod = FunctionEncoder.encode(function);
        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice,
                DefaultGasProvider.GAS_LIMIT, MAINNET_TEST_CONTRACT_ADDRESS, encodedMethod);


        System.out.print(" signing transaction");


        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

        if (ethSendTransaction.hasError()) {
            System.err.print(" ERROR: " + ethSendTransaction.getError().getMessage() + " :" + ethSendTransaction.getError().getCode() + ":");
            if (ethSendTransaction.getError().getCode() == -32000) {
                System.out.println("Retrying the transaction");
                Thread.sleep(50000);
                return processWorkItem(wi);
            } else {

                throw new Exception("The transaction contains a lot of errors that can't be recovered");
            }
        } else {

            String transactionHash = ethSendTransaction.getTransactionHash();

            System.out.println(" Transaction hash:  " + transactionHash);

            EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(transactionHash).send();

            // polling for the receipt. We could probably use a transaction processor?
            // Notably, it's similar to: https://github.com/web3j/web3j/blob/master/integration-tests/src/test/java/org/web3j/protocol/scenarios/Scenario.java.
            while (receipt.getResult() == null) {
                receipt = web3j.ethGetTransactionReceipt(transactionHash).send();
                Thread.sleep(2000);
                System.out.print("/waiting receipt/");
            }

            TransactionReceipt rec = receipt.getResult();
            WorkItem winew = new WorkItem();
            winew.setWhen(LocalDateTime.now());
            winew.setDone(true);
            winew.setAmount(wi.getAmount());
            winew.setAddress(wi.getAddress());
            winew.setReceipt(String.join("/", rec.getBlockNumber().toString(), rec.getCumulativeGasUsed().toString(), rec.getGasUsed().toString(), rec.getTransactionHash()));
            System.out.print(" " + winew.toString());
            return winew;
        }
    }

    /**
     * Creates a work item
     *
     * @param u The entry in the csv file
* @return the work item
     */
    private static WorkItem makeWorkItem(String u) {
        String[] splitted = u.split(";");
        WorkItem wi = new WorkItem();

        if (splitted.length == 2) {
            wi.setAddress(splitted[0]);
            wi.setAmount(calculateAmount(Integer.parseInt(splitted[1])));

        } else if (splitted.length == 5) {
            wi.setAddress(splitted[0]);
            wi.setAmount(new BigInteger(splitted[1]));
            wi.setDone(Boolean.valueOf(splitted[2]));


            wi.setWhen(LocalDateTime.parse(splitted[3], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            wi.setReceipt(splitted[4]);

        }

        return wi;
    }

    /**
     * Calculate the amount by multiplying the number of GVINE * 10^18.
     *
     * @param value the value of GVINE to be sent
     * @return value * 10^18.
     */
    private static BigInteger calculateAmount(int value) {
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(10).pow(18));
    }

    /**
     * Return the address, from the csv file
     * @param o the csv line
     * @return the address (which is the first entry)
     */
    private static String extractMethod(String o) {
        String value = (String) o.split(";")[0];
        out.println("Operating on " + value);
        return value ;
    }


    /**
     * The work item
     */
    private static class WorkItem {

        String address;
        BigInteger amount;
        boolean done;
        LocalDateTime when;
        String receipt;

        public String getAddress() {
            return address;
        }

        @Override
        public String toString() {
            return String.join(";", address, String.valueOf(amount), String.valueOf(done),
                    when != null ? when.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                    receipt != null ? receipt : "", System.lineSeparator());

        }

        public void setAddress(String address) {
            this.address = address;
        }

        public BigInteger getAmount() {
            return amount;
        }

        public void setAmount(BigInteger amount) {
            this.amount = amount;
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        public LocalDateTime getWhen() {
            return when;
        }

        public void setWhen(LocalDateTime when) {
            this.when = when;
        }

        public String getReceipt() {
            return receipt;
        }

        public void setReceipt(String receipt) {
            this.receipt = receipt;
        }


    }

    public static void main(String[] args) throws IOException, CipherException {

        if (args.length < 1) {
            out.println("Password not given");
    //        exit(-1);
        }

        Web3j web3 = connectToNetwork();

        DistributeToken dt = new DistributeToken();
        dt.process(args, web3);

        System.out.println("ALl done");
        System.exit(0);
    }

}
