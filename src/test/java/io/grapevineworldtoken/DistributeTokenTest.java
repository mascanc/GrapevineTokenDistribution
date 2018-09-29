package io.grapevineworldtoken;

import io.grapevineworldtoken.contract.generated.GrapevineToken;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DistributeTokenTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
    }

    @Test
    void testSendTokensAndCheckForValidResults() throws Exception {


        String password="";


        Web3j web3 = Web3j.build(new HttpService("https://mainnet.infura.io"));
        DistributeToken dt = new DistributeToken();
        dt.process(new String[]{password}, web3);


        // now make sure that I have the correct values
        GrapevineToken gt = dt.getContract();

        assertTrue(gt.balanceOf("0x5a93c509c690f5c2858b73ef171925538eb9c8fe").send().intValue() == 50);
        assertTrue(gt.balanceOf("0x173eccc4f3874e5ef60a68e6a06e9b799410e209").send().intValue() == 20);
        assertTrue(gt.balanceOf("0x0762e0f09f6170da8e332c07f06f1d5979683ed8").send().intValue() == 52);
        assertTrue(gt.balanceOf("0x77096da04d4d228b5ef6ef49eba0bff907cfd292").send().intValue() == 53);
        assertTrue(gt.balanceOf("0x87cc8000752eb9fb5cad14a3e8422fa27e7fc277").send().intValue() == 54);
        assertTrue(gt.balanceOf("0x5c633e3fbef65975e5c3080570533a811aa3eeb2").send().intValue() == 55);
        assertTrue(gt.balanceOf("0xad2c97ce823c07b64422055181e46978b35b70f8").send().intValue() == 56);
        assertTrue(gt.balanceOf("0x1c5c6ec0911281348aa1745f2551425fa23d217b").send().intValue() == 57);
        assertTrue(gt.balanceOf("0x12a88f9d2c27f71f4d9f793324252062c3e6c63d").send().intValue() == 58);
        assertTrue(gt.balanceOf("0xa4ec7ca17d940c78b6741fbd76b5ef069fb6faef").send().intValue() == 59);
        assertTrue(gt.balanceOf("0xd5760731df0005bd4763dcafb274cfd55d6b2d01").send().intValue() == 60);
        assertTrue(gt.balanceOf("0xa83d29ae19734210a7505758c70fbc76a9a511d9").send().intValue() == 61);
        assertTrue(gt.balanceOf("0x30b659f93bf5fcf529f4f78ce44f1981aceb353d").send().intValue() == 62);
        assertTrue(gt.balanceOf("0x23a34dff69d1a2b8cd1eccfce274c8ad53cd6547").send().intValue() == 63);
        assertTrue(gt.balanceOf("0xf925e02546d41e3606ab9644db414528a3b0d059").send().intValue() == 64);
        assertTrue(gt.balanceOf("0xee5d0821f47909240f7e558c9abd1a76679222d2").send().intValue() == 65);
        assertTrue(gt.balanceOf("0x033341fb428b9375d57237e17c471e3c8b31195b").send().intValue() == 66);
        assertTrue(gt.balanceOf("0xb4b2f0b1d20ec543147b59e8302a5daafed1fd1d").send().intValue() == 67);
        assertTrue(gt.balanceOf("0x3a1d3c8f9aeac2bfafc8c3ac4318fd31e04fa5bd").send().intValue() == 68);
    }
}
