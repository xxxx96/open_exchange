package com.openexchange.wallet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 钱包服务启动类。@EnableAsync：充值到账通知等异步任务依赖此开关。
 */
@EnableAsync
@SpringBootApplication
public class WalletServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(WalletServiceApplication.class,args);
    }
}
