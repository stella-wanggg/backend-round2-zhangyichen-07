package com.example.exception;

import com.example.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleBusinessException(BusinessException e) { // 接页码为负之类的不在参数校验范围的异常
        log.warn("用户输入有误:{}",e.getMessage()); // 输出日志到控制台(用户不按规矩出牌,只输出信息就行)
        return Result.error(400,e.getMessage());

    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) { // 接非空检验的报错
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors(); // 获取所有报错信息
        String defaultMessage = fieldErrors.get(0).getDefaultMessage(); // 取得第一条报错信息,提示的同时防止信息泄露
        log.warn("用户输入内容检验有误:{}",defaultMessage);
        return Result.error(400,defaultMessage);
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result IllegalArgumentException(IllegalArgumentException e){
        log.warn("用户输入的密码算法不支持",e.getMessage());
        return Result.error(400,"不支持的密码算法");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNoResourceFoundException(NoResourceFoundException e){
        log.error("请求路径不存在:",e.getMessage());
        return Result.error(404,"请求路径不存在");
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e){
        log.error("请求方法有误",e.getMessage());
        return Result.error(405,"请求方法有误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result systemExceptiom(Exception e){ // 接剩下的系统错误
        log.error("系统出错:",e); // 报错信息展示在控制台(展示完整异常,方便修改)
        return Result.error(500,"异常啦!请联系程序员");
    }

}
