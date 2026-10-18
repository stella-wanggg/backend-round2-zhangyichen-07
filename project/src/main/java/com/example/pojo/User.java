package com.example.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Integer id;
    @NotBlank(message = "用户名不能为空")
    @Size(max = 20,message = "用户名最长20位")
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6,message = "密码最少6位")
    @Pattern(regexp = "^[A-Za-z0-9_]+$",message = "密码只能包含大小写字母、数字、下划线")
    private String password;
}
