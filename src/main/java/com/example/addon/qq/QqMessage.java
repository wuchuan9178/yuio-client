package com.example.addon.qq;

import java.util.List;

/**
 * 一条要显示在灵动岛上的 QQ 消息。
 *
 * @param title     群名或联系人昵称
 * @param sender    发送者昵称
 * @param avatarUrl 头像地址, 拿不到就是 null
 * @param groupId   群号, 私聊是 -1
 * @param mention   群里是否 @了你 / @全体成员
 * @param segments  消息分段, 每段带自己的颜色
 * @param born      到达时间, 用于淡出
 */
public record QqMessage(String title, String sender, String avatarUrl, long groupId,
                        boolean mention, List<QqSegment> segments, long born) {
}
