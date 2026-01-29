package com.example.kjlgraphdemo.entity;

public class FoodIteam {
    private Long foodid;
    private String foodname;
    private String foodurl;

    // 默认构造函数
    public FoodIteam() {
    }

    // 带参数的构造函数
    public FoodIteam(String foodname, String foodurl) {
        this.foodname = foodname;
        this.foodurl = foodurl;
    }

    // Getter 和 Setter 方法
    public Long getFoodid() {
        return foodid;
    }

    public void setFoodid(Long foodid) {
        this.foodid = foodid;
    }

    public String getFoodname() {
        return foodname;
    }

    public void setFoodname(String foodname) {
        this.foodname = foodname;
    }

    public String getFoodurl() {
        return foodurl;
    }

    public void setFoodurl(String foodurl) {
        this.foodurl = foodurl;
    }
}
