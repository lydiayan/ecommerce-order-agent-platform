package com.example.mallorder.controller;

import com.example.mallorder.entity.Product;
import com.example.mallorder.service.ProductService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/products")
@Log4j2
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 根据分类ID获取商品列表
     * @param categoryId 分类ID
     * @return 商品列表
     */
    @GetMapping("/category/{categoryId}")
    public List<Product> getProductsByCategory(@PathVariable int categoryId) {
        log.info("根据分类ID获取商品列表");
        return productService.getProductsByCategory(categoryId);
    }

    /**
     * 根据商品名称搜索商品
     * @param productName 商品名称
     * @return 商品列表
     */
    @GetMapping("/search")
    public List<Product> getProductsByName(@RequestParam String productName) {
        log.info("根据商品名称搜索商品");
        return productService.getProductsByName(productName);
    }

    /**
     * 添加商品
     * @param product 商品信息
     * @return 受影响的行数
     */
    @PostMapping
    public int addProduct(@RequestBody Product product) {
        log.info("添加商品");
        return productService.addProduct(product);
    }

    /**
     * 更新商品信息
     * @param product 商品信息
     * @return 受影响的行数
     */
    @PutMapping
    public int updateProduct(@RequestBody Product product) {
        log.info("更新商品信息");
        return productService.updateProduct(product);
    }

    /**
     * 删除商品
     * @param productId 商品ID
     * @return 受影响的行数
     */
    @DeleteMapping("/{productId}")
    public int deleteProduct(@PathVariable int productId) {
        log.info("删除商品");
        return productService.deleteProduct(productId);
    }
}
