package com.css.test;

import com.css.test.vo.Goods;
import com.css.test.vo.PaymentHackData;
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootApplication
public class TestApplication {

    public static void main(String[] args)  {

        SpringApplication.run(TestApplication.class, args);
    }

}
