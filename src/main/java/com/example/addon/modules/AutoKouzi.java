package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.*;

public class AutoKouzi extends Module {
    public enum Order { TopDown, BottomUp, Random }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delayMin = sgGeneral.add(new IntSetting.Builder()
        .name("delay-min").description("Min delay in ms.").defaultValue(1000).min(0).build());

    private final Setting<Integer> delayMax = sgGeneral.add(new IntSetting.Builder()
        .name("delay-max").description("Max delay in ms.").defaultValue(2000).min(0).build());

    private final Setting<Boolean> privateMode = sgGeneral.add(new BoolSetting.Builder()
        .name("private-mode").description("Send via private message command.").defaultValue(false).build());

    private final Setting<String> privateCmd = sgGeneral.add(new StringSetting.Builder()
        .name("private-cmd").description("%target% = target, %message% = content")
        .defaultValue("msg %target% %message%").visible(privateMode::get).build());

    private final Setting<String> privateTarget = sgGeneral.add(new StringSetting.Builder()
        .name("private-target").description("Target player name.")
        .defaultValue("Steve").visible(privateMode::get).build());

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages").description("Messages to send.")
        .defaultValue(loadDefaults())
        .build());

    private final Setting<Order> order = sgGeneral.add(new EnumSetting.Builder<Order>()
        .name("order").description("Sending order.").defaultValue(Order.TopDown).build());

    private final Setting<Boolean> noRepeat = sgGeneral.add(new BoolSetting.Builder()
        .name("no-repeat").description("Avoid duplicate random picks.").defaultValue(true).build());

    //文本
    private static final String RAW = """
        操你妈的，能不能告诉我
        你为什么只能念三字经的
        你怎么翻来覆去辗转反侧就这几个虎头蛇尾的破烂词汇
        你是不是不敬神爹我的的废物
        哈哈哈狗东西开始模仿你伟大的神爹我了
        是不是窝囊废的告诉我窝囊废
        能不能跪下对着我主页磕头磕到流血的
        小乐逼是不是能不能别像狗似的
        为什么我一捡石头你就跑的啊
        是不是拾人牙慧却如获至宝的
        宝贝能不能别窥屏的
        嘻嘻嘻你的速度呢
        你拖拉机的玩意
        主播我把你老妈子操了114514次你怎么才打出这一行稀稀拉拉没有丝毫攻击力的泛滥句子
        废物能不能来救救你的老妈子的
        你是不是故意在磨磨唧唧
        能不能让我拿出实力的
        小废物滚出来的
        乐子你老妈子的血烂臭骚b都快被我磨坏了的你怎么还没滚出来
        废物东西叫个残疾人出来都能战胜你的
        你为什么为你盗词的无能行为沾沾自喜的
        小伙子面对我是不是无能为力的
        小狗子滚出来的
        躲在屏幕背后就有安全感了是吧
        主播是不是面对我的速度只能无能为力莫名其妙
        操你老母你怎么都不反抗的
        你野爹我好像把你老妈子的乳房割下来边操边吃的
        废狗东西还是赶紧滚吧
        哈哈哈你的破b脸子大概挂不住了吧
        你为什么拿着你那拖拉机速度耀武扬威的
        你神爹我对你仁慈不是我的责任
        你废物了狗子
        你无能为力你无力回天你树倒猢狲散
        你的破老妈子也救不了你
        呜呜呜谁叫你惹我的
        是你自己惹我的你为什么跑我这碰瓷的
        你的鬼哭狼嚎没有丝毫的杀伤力知道吗
        滚出宣布你是废物行不行
        你的不堪一击实在给我逗乐了
        你以后见到我要闻风丧胆的知道吗
        你怎么磨磨唧唧三脚两步死活挤不出半个字的
        你这破批样子为什么出来丢脸的
        你为什么负隅顽抗不立刻滚出来跪下磕头的
        哈哈哈人类的缺点淋漓尽致在你身上展示给大家看了
        你的人格未来乃至你的破老妈子在我面前都是如此不堪一击知道吗
        为什么拿你妈来开玩笑
        操你妈的行不行的为什么不说话
        你个废物为什么有脸上网逼逼赖赖磨磨唧唧叽叽喳喳的
        你个吃流食的无能玩意能不能滚出来告诉我你有多废物
        你哪儿来的脸苟延残喘
        瞎骂小鬼能不能拿出思维逻辑
        你是不是被我痛打到高位截瘫
        哈哈哈你的破烂自尊呢
        你为什么三言两语就让你神爹我定格了你残缺不全的人格
        你这恶心的样子可真是光宗耀祖辣
        你神爹我正想着怎么灭了你这没脸没皮没羞没臊的东西
        别老用你的破烂词汇格挡我华丽的词藻
        你能不能提前跪下和我扣的
        废物东西你是不是在用眼睛和我按字的
        你什么成分什么排面什么玩意什么东西敢更我扣的
        你能不能见到你伟大无敌的神爹我就感动的自愿献出老妈的
        你这只牲畜是不是紧张了的
        你的负隅顽抗没有一丝一毫的意义知道吗
        你能不能不要一边泪流满面一边跟我神爹按字的
        你这只王八是不是只能龟缩在屏幕背后
        我吧你老妈子先奸后杀在香喷喷的烹饪了的
        你是不是不知道速度这东西
        狗玩意你怎么沾沾自喜自娱自乐认不清你行将就木的现实
        你这改扔进垃圾桶的速度
        你登顶人极的神爹很不喜欢你的废狗速度的
        你的蜗牛速度和我简直不在一个计量单位的
        你是不是不知道你是低能的还是死皮赖脸
        告诉我我现在操你妈合适吗
        你对于你老妈子处女膜怎么破的是不是还云里雾里
        无能贱种为什么忤逆你无可匹敌的神爹我
        你这条废狗害你老妈子给我卖淫赚钱的知道吗
        赶快自杀然后发我视频就饶了你
        小狗子你惹了祸事就要负责的知道吗
        你假装成熟的把戏在你见多识广的神爹面前就是小品知道吗
        你能不能不要满脸气急败坏的和我按
        你心悦诚服我跪下的不然给你老妈子扬了
        你看到我是不是打心眼子里发凉啊
        你放松点我只是要你自杀而已
        狗玩意能不能痛哭流涕的认错然后主动杀了你妈咪给我谢罪的
        你早已无能为力
        小乐子你继续浑浑噩噩的念你的三字经
        你不要畏畏缩缩出来无能为力的攻击我固若金汤的词藻城墙好不好
        你用你侏罗纪的词汇反抗我的是不是
        小废物不知道天高地厚的就回去当井底之蛙行不行
        你狗刨式的稀稀拉拉的叽叽喳喳的在叫唤些什么污言秽语
        能不能人模狗样的出来灰头土脸的回去
        你为什么抵抗我
        你死皮赖脸的瞎扒拉些什么无用言论
        要不要你神爹我出来戳穿你支离破碎稀稀拉拉口齿不清的
        我们间的差距不是你废物似的词汇可以比拟的
        你半身不遂高位截瘫流着口水跟我按字是吧
        你个井底之蛙大概以为你登峰造极了的
        你能不能让我勉强拿出战斗力
        狗玩意你充其量也就是欺负下胎儿吧
        次狗你神爹我告诉你但凡是个长了手都能把你吊起来凌辱
        能不能自我凌迟给我看的
        你是不是准备磕头了
        废物能不能回答我
        能不能告诉我的
        你乱七八糟的思维逻辑你自己也理不清楚吧
        你的词汇什么成分也就内样
        你为什么出头
        你为什么进行无用的抵抗
        你再叫神爹我要开始打狗了
        你现在是不是绞尽脑汁手心流汗思考这什么词汇才能坚持你不堪一击的立场
        我希望你能够记住此刻的紧张这样下辈子你就知道不要随便出头的
        你血妈都被我来来往往车水马龙辗转反侧的操了一遍一遍又一遍的
        你能不能滚出来接受你无力回天的无能事实
        哈哈哈屏幕前的你是不是条狗的
        你狗急跳墙的速度终于有望媲美你神爹我三岁的速度辣
        哈哈哈你的b脸呢
        你语无伦次没有一丝一毫的作用
        小子你怎么宣布你要造反灭我登顶人极的神爹的
        小伙子在键盘上敲出个我错了再自杀可比你现在负隅顽抗来的简单的
        我现在就宣布你是废物你也无能为力
        你是不是觉得你神爹无聊的殴打你很难
        我能不能往你亲妈坟里面塞炸弹就像从前往你妈b里面塞炸弹一样
        你能不能不要哭泣的反抗我的
        你是不是还没明白你必死无疑的局势
        好像就你云里雾里的
        小垃圾能不能拿出你的浑身解数的
        赫赫你是不是浑身冒汗害怕我把你吊起来凌迟
        我不要你这儿子了怎么办
        你就这文化水平好像神农架野人更我按字的
        """;

    private int timer = 0;
    private int index = 0;
    private final Set<Integer> used = new HashSet<>();
    private final Random random = new Random();

    public AutoKouzi() {
        super(AddonTemplate.OTHER, "auto-kouzi", "喷蛆最爱。");
    }

    private static List<String> loadDefaults() {
        List<String> out = new ArrayList<>();
        for (String line : RAW.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) out.add(line);
        }
        return out.isEmpty() ? List.of("你好") : out;
    }

    @Override
    public void onDeactivate() {
        timer = 0;
        index = 0;
        used.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }
        if (messages.get().isEmpty()) return;

        timer = randomDelay();
        sendText(nextMessage());
    }

    private int randomDelay() {
        int lo = Math.min(delayMin.get(), delayMax.get());
        int hi = Math.max(delayMin.get(), delayMax.get());
        if (lo == hi) return lo;
        return lo + random.nextInt(hi - lo + 1);
    }

    private String nextMessage() {
        List<String> list = messages.get();
        int n = list.size();
        switch (order.get()) {
            case TopDown -> {
                index = (index + 1) % n;
                return list.get(index);
            }
            case BottomUp -> {
                index = (index - 1 + n) % n;
                return list.get(index);
            }
            case Random -> {
                if (noRepeat.get() && n > 1) {
                    if (used.size() >= n) used.clear();
                    int i;
                    do { i = random.nextInt(n); } while (used.contains(i));
                    used.add(i);
                    return list.get(i);
                }
                return list.get(random.nextInt(n));
            }
        }
        return list.get(0);
    }

    private void sendText(String text) {
        if (text == null || text.isEmpty()) return;
        String out = privateMode.get()
            ? privateCmd.get().replace("%target%", privateTarget.get()).replace("%message%", text)
            : text;
        ChatUtils.sendPlayerMsg(out);
    }
}
