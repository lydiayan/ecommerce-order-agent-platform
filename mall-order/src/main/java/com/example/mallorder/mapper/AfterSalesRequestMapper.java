package com.example.mallorder.mapper;

import com.example.mallorder.entity.AfterSalesRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AfterSalesRequestMapper {

    @Insert("""
            INSERT INTO after_sales_request
                (ticket_id, order_id, user_id, operation_type, reason_type, reason_description,
                 evidence_urls, customer_opened, customer_used, customer_condition_status,
                 eligibility_decision, policy_version, active_request_key, status)
            VALUES
                (#{ticketId}, #{orderId}, #{userId}, #{operationType}, #{reasonType}, #{reasonDescription},
                 #{evidenceUrls,typeHandler=com.example.mallorder.mapper.StringListJsonTypeHandler},
                 #{customerOpened}, #{customerUsed}, #{customerConditionStatus},
                 #{eligibilityDecision}, #{policyVersion}, #{activeRequestKey}, #{status})
            ON DUPLICATE KEY UPDATE active_request_key = VALUES(active_request_key)
            """)
    int insertOrKeepExisting(AfterSalesRequest request);

    @Select("SELECT * FROM after_sales_request WHERE ticket_id = #{ticketId}")
    @Results(id = "afterSalesResult", value = {
            @Result(property = "evidenceUrls", column = "evidence_urls",
                    typeHandler = StringListJsonTypeHandler.class)
    })
    AfterSalesRequest selectByTicketId(@Param("ticketId") String ticketId);

    @Select("SELECT * FROM after_sales_request WHERE user_id = #{userId} ORDER BY created_at DESC")
    @ResultMap("afterSalesResult")
    List<AfterSalesRequest> selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM after_sales_request WHERE active_request_key = #{activeRequestKey} LIMIT 1")
    @ResultMap("afterSalesResult")
    AfterSalesRequest selectByActiveRequestKey(@Param("activeRequestKey") String activeRequestKey);
}
