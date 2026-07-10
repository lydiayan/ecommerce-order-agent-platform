package com.css.test;

import com.css.test.vo.Goods;
import com.css.test.vo.PaymentHackData;
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
public class tableServicesTest {
    String resource = "render_hackloop.docx";
    PaymentHackData data = new PaymentHackData();

    @BeforeEach
    public void init() {
        List<Goods> goods = new ArrayList<>();
        Goods good = new Goods();
        good.setCount(4);
        good.setName("墙纸");
        good.setDesc("书房卧室");
        good.setDiscount(1500);
        good.setPrice(400);
        good.setTax(new Random().nextInt(10) + 20);
        good.setTotalPrice(1600);
        goods.add(good);
        goods.add(good);
        goods.add(good);
        data.setGoods(goods);



        data.setTotal("1024");

        data.setGoods2(goods);


    }



    @Test
    public void testPaymentHackExample() throws Exception {
        LoopRowTableRenderPolicy hackLoopTableRenderPolicy = new LoopRowTableRenderPolicy();
        Configure config = Configure.builder().bind("goods", hackLoopTableRenderPolicy)
              .build();
        XWPFTemplate template = XWPFTemplate.compile(resource, config).render(data);
        template.writeToFile("target/out_render_looprow.docx");
    }
}
