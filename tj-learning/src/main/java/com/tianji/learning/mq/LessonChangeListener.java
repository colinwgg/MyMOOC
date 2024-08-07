package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService learningLessonService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
                    exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = MqConstants.Key.ORDER_PAY_KEY
            )
    )
    public void listenLessonPay(OrderBasicDTO dto) {
        if (dto == null || dto.getUserId() == null || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
        log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", dto.getUserId(), dto.getOrderId(), dto.getCourseIds());
        learningLessonService.addUserLessons(dto.getUserId(), dto.getCourseIds());
    }
}
