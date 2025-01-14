package com.xuyuan.xyojbackendjudgeservice.judge.codesandbox.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.xuyuan.xyojbackendcommon.common.ErrorCode;
import com.xuyuan.xyojbackendcommon.exception.BusinessException;
import com.xuyuan.xyojbackendjudgeservice.judge.codesandbox.CodeSandbox;
import com.xuyuan.xyojbackendmodel.model.codesandbox.ExecuteCodeRequest;
import com.xuyuan.xyojbackendmodel.model.codesandbox.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 远程代码沙箱（实际调用接口的沙箱）
 */
@Slf4j
public class RemoteCodeSandbox implements CodeSandbox {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "authHeader";

    private static final String AUTH_REQUEST_SECRET = "secretKey";


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        System.out.println("远程代码沙箱");
        String url = "http://47.120.70.248:8090/executeCode";
//        String url = "http://localhost:8090/executeCode";
        String json = JSONUtil.toJsonStr(executeCodeRequest);

        String responseStr = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER, AUTH_REQUEST_SECRET)
                .body(json)
                .execute()
                .body();
        System.out.println("远程调用代码沙箱发送成功");
        if (StringUtils.isBlank(responseStr)) {
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "executeCode remoteSandbox error, message = " + responseStr);
        }
        ExecuteCodeResponse executeCodeResponse = JSONUtil.toBean(responseStr, ExecuteCodeResponse.class);
//        System.out.println("远程代码沙箱返回信息：" + executeCodeResponse);
        return executeCodeResponse;
    }
}
