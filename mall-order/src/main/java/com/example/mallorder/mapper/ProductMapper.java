package com.example.mallorder.mapper;


import com.example.mallorder.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM products WHERE product_id = #{productId}")
    List<Product> getProductsById(@Param("productId") int productId);

    /**
     * 通过分类ID查询该分类下的所有商品
     *
     * @param categoryId 分类ID
     * @return 商品列表
     */
    @Select("SELECT * FROM products WHERE category_id = #{categoryId}")
    List<Product> getProductsByCategory(@Param("categoryId") int categoryId);

    /**
     * 通过产品名称查询商品
     *
     * @param productName 产品名称
     * @return 商品列表
     */
    @Select("SELECT * FROM products WHERE product_name LIKE CONCAT('%', #{productName}, '%')")
    List<Product> getProductsByName(@Param("productName") String productName);

    /**
     * 插入一个新的商品
     *
     * @param product 商品对象
     * @return 影响的行数
     */
    @Insert("INSERT INTO products (product_name, product_description, category_id, brand_id, supplier_id, price, stock_quantity, main_image_url) " +
            "VALUES (#{product.productName}, #{product.productDescription}, #{product.categoryId}, #{product.brandId}, #{product.supplierId}, #{product.price}, #{product.stockQuantity}, #{product.mainImageUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "product.product_id")
    int insertProduct(@Param("product") Product product);

    /**
     * 更新商品信息
     *
     * @param product 商品对象
     * @return 影响的行数
     */
    @Update("UPDATE products SET product_name = #{product.productName}, product_description = #{product.productDescription}, category_id = #{product.categoryId}, brand_id = #{product.brandId}, supplier_id = #{product.supplierId}, price = #{product.price}, stock_quantity = #{product.stockQuantity}, main_image_url = #{product.mainImageUrl} WHERE product_id = #{product.product_id}")
    int updateProduct(@Param("product") Product product);

    /**
     * 删除一个商品
     *
     * @param productId 商品ID
     * @return 影响的行数
     */
    @Delete("DELETE FROM products WHERE product_id = #{productId}")
    int deleteProduct(@Param("productId") int productId);
}
