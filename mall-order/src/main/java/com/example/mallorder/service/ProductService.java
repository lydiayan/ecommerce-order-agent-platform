package com.example.mallorder.service;
import com.example.mallorder.entity.Product;
import com.example.mallorder.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    public List<Product> getProductsByCategory(int categoryId) {
        return productMapper.getProductsByCategory(categoryId);
    }

    public List<Product> getProductsByName(String productName) {
        return productMapper.getProductsByName(productName);
    }

    public int addProduct(Product product) {
        return productMapper.insertProduct(product);
    }

    public int updateProduct(Product product) {
        return productMapper.updateProduct(product);
    }

    public int deleteProduct(int productId) {
        return productMapper.deleteProduct(productId);
    }


}
