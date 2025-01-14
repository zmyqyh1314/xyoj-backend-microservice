package com.xuyuan.xyojbackendjudgeservice.judge;

import cn.hutool.json.JSONUtil;
import com.xuyuan.xyojbackendcommon.common.ErrorCode;
import com.xuyuan.xyojbackendcommon.exception.BusinessException;
import com.xuyuan.xyojbackendjudgeservice.judge.codesandbox.CodeSandbox;
import com.xuyuan.xyojbackendjudgeservice.judge.codesandbox.CodeSandboxFactory;
import com.xuyuan.xyojbackendjudgeservice.judge.codesandbox.CodeSandboxProxy;
import com.xuyuan.xyojbackendjudgeservice.judge.strategy.JudgeContext;
import com.xuyuan.xyojbackendmodel.model.codesandbox.ExecuteCodeRequest;
import com.xuyuan.xyojbackendmodel.model.codesandbox.ExecuteCodeResponse;
import com.xuyuan.xyojbackendmodel.model.codesandbox.JudgeInfo;
import com.xuyuan.xyojbackendmodel.model.dto.question.JudgeCase;
import com.xuyuan.xyojbackendmodel.model.entity.Question;
import com.xuyuan.xyojbackendmodel.model.entity.QuestionSubmit;
import com.xuyuan.xyojbackendmodel.model.enums.QuestionSubmitStatusEnum;
import com.xuyuan.xyojbackendserviceclient.service.QuestionFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:example}")
    private String type;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionFeignClient.getQuestionSubmitById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionFeignClient.getQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 2）如果题目提交状态不为等待中，就不用重复执行了
        if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }
        // 3）更改判题（题目提交）的状态为 “判题中”，防止重复执行
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean update = questionFeignClient.updateQuestionSubmitById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        // 4）调用沙箱，获取到执行结果
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        // 获取测试输入、输出用例
        String judgeCaseStr = question.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> testInputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        List<String> testOutputList = judgeCaseList.stream().map(JudgeCase::getOutput).collect(Collectors.toList());
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(testInputList)
                .build();
        // 获得沙箱运行信息
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        List<String> runOutputList = executeCodeResponse.getOutputList();
        // 5）根据沙箱的执行结果，设置题目的判题状态和信息
        JudgeContext judgeContext = new JudgeContext();
        judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
        judgeContext.setTestOutputList(testOutputList);
        judgeContext.setRunOutputList(runOutputList);
//        judgeContext.setJudgeCaseList(judgeCaseList);
        judgeContext.setQuestion(question);
        judgeContext.setQuestionSubmit(questionSubmit);
        // 得到修改的结果
        JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);
        System.out.println("判断后得到的结果" + judgeInfo);
        // 6）修改数据库中的判题结果
        questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        update = questionFeignClient.updateQuestionSubmitById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        QuestionSubmit questionSubmitResult = questionFeignClient.getQuestionSubmitById(questionId);
        return questionSubmitResult;
    }
}
