package com.lts.jobtracker.processor;

import com.lts.biz.logger.domain.JobLogPo;
import com.lts.biz.logger.domain.LogType;
import com.lts.core.constant.Level;
import com.lts.core.logger.Logger;
import com.lts.core.logger.LoggerFactory;
import com.lts.core.protocol.JobProtos;
import com.lts.core.protocol.command.JobCancelRequest;
import com.lts.core.support.JobDomainConverter;
import com.lts.core.support.SystemClock;
import com.lts.jobtracker.domain.JobTrackerAppContext;
import com.lts.queue.domain.JobPo;
import com.lts.remoting.Channel;
import com.lts.remoting.exception.RemotingCommandException;
import com.lts.remoting.protocol.RemotingCommand;

/**
 * @author Robert HG (254963746@qq.com) on 11/7/15.
 */
public class JobCancelProcessor extends AbstractRemotingProcessor {

    private final Logger LOGGER = LoggerFactory.getLogger(JobCancelProcessor.class);

    public JobCancelProcessor(JobTrackerAppContext appContext) {
        super(appContext);
    }

    @Override
    public RemotingCommand processRequest(Channel channel, RemotingCommand request) throws RemotingCommandException {

        JobCancelRequest jobCancelRequest = request.getBody();

        String taskId = jobCancelRequest.getTaskId();
        String taskTrackerNodeGroup = jobCancelRequest.getTaskTrackerNodeGroup();
        JobPo jobPo = appContext.getCronJobQueue().getJob(taskTrackerNodeGroup, taskId);
        if (jobPo == null) {
            jobPo = appContext.getExecutableJobQueue().getJob(taskTrackerNodeGroup, taskId);
        }

        if (jobPo != null) {
            appContext.getExecutableJobQueue().removeBatch(jobPo.getRealTaskId(), jobPo.getTaskTrackerNodeGroup());
            if (jobPo.isCron()) {
                appContext.getCronJobQueue().remove(jobPo.getJobId());
            } else if (jobPo.isRepeatable()) {
                appContext.getRepeatJobQueue().remove(jobPo.getJobId());
            }
            // 记录日志
            JobLogPo jobLogPo = JobDomainConverter.convertJobLog(jobPo);
            jobLogPo.setSuccess(true);
            jobLogPo.setLogType(LogType.DEL);
            jobLogPo.setLogTime(SystemClock.now());
            jobLogPo.setLevel(Level.INFO);
            appContext.getJobLogger().log(jobLogPo);

            LOGGER.info("Cancel Job success , jobId={}, taskId={}, taskTrackerNodeGroup={}", jobPo.getJobId(), taskId, taskTrackerNodeGroup);
            return RemotingCommand.createResponseCommand(JobProtos
                    .ResponseCode.JOB_CANCEL_SUCCESS.code());
        }

        return RemotingCommand.createResponseCommand(JobProtos
                .ResponseCode.JOB_CANCEL_FAILED.code(), "Job maybe running");
    }
}
