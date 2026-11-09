package com.openexchange.wallet;
import com.openexchange.wallet.chain.ChainProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 钱包服务启动类。@EnableAsync：充值到账通知等异步任务依赖此开关；
 * @EnableConfigurationProperties：链上节点连接配置（Sepolia RPC / testnet4 网关）。
 */
@EnableAsync
@EnableConfigurationProperties(ChainProperties.class)
@SpringBootApplication
public class WalletServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(WalletServiceApplication.class,args);
    }
}
