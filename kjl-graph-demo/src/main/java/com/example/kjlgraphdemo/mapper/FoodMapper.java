package com.example.kjlgraphdemo.mapper;

import com.example.kjlgraphdemo.entity.FoodIteam;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FoodMapper {

    @Select("SELECT foodname FROM foods WHERE foodname LIKE CONCAT('%', #{foodname}, '%')")
    List<String> findByFoodnameLike(@Param("foodname") String foodname);

    @Insert("INSERT INTO foods (foodname, foodurl) VALUES (#{foodname}, #{foodurl})")
    @Options(useGeneratedKeys = true, keyProperty = "foodid")
    int insertFood(FoodIteam food);
}
