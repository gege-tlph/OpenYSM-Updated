package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.condition.ConditionChair;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionManager;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionVehicle;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.apache.commons.lang3.StringUtils;
import rip.ysm.compat.swem.SWEMCompat;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

/**
 * 女仆坐姿动画的垂直修正量。
 *
 * <h2>为什么需要它</h2>
 * 原版骑乘语义是「乘客的**脚底**落在挂点上」——`getPassengerRidingPosition` 给出的
 * 马 1.44375 / 猪 0.86875 / 船 0.1875 都是按站立模型的脚底原点定的，vanilla 靠把腿掰弯
 * 来表现坐姿。而 YSM 播的是模型作者做的坐姿动画，其模型根在臀部一带，不在脚底；
 * 把根放到原版挂点上，人就整体浮在座位上方。fork 的玩家链与女仆链**都**没有这项补偿
 * （逐项比对过 {@code GeoReplacedEntityRenderer.renderEntityWithTexture} 与
 * {@code MaidGeoRenderer.renderMaid}），本类只补女仆侧——玩家侧无实测症状，不顺手改。
 *
 * <h2>常量从哪来</h2>
 * 复用「fix(preview): 动画预览界面的载具与床渲染」(6eb649f) 里那张经实机验收的表
 * （{@code ModelPreviewRenderer} 的 poseYOffset）。它的语义是
 * <b>「载具原点 + 常量 = 模型根应在的高度」</b>：预览里载具恒定落在地面，而内层坐标系
 * 地面顶面就是 y=0，所以那批常量正是相对载具原点的。世界里渲染器拿到的根高度是
 * 「载具原点 + 挂点」，两者一减就是修正量：
 * <pre>
 *   correction = 常量 − (vehicle.getPassengerRidingPosition(maid).y − vehicle.getY())
 * </pre>
 * <b>运行时算、不要写死</b>：{@code ride} 对**任何带鞍 Mob** 都会命中，骆驼 / 驴 / 炽足兽的
 * 挂点各不相同，写死马的 1.44375 会让它们各错各的。
 * <p>
 * ⚠️ 那笔提交本身的修法不可套用（三个成因全在 GUI 侧：submit 化的 GUI 实体渲染、床的方块模型
 * 没有几何、renderToTexture 的 translate/mulPose 顺序），世界内都不存在。可复用的只有常量与
 * 那条警告：<b>不要反过来用原版挂点去决定人物高度</b>，实测那样做三种载具一律浮起来。
 *
 * <h2>2026-07-31 实机调校记录（含一条被推翻的假设，勿重蹈）</h2>
 * 四条里<b>只有 {@link #POSE_RIDE} 真正需要改</b>（0.85 → 0.90）；{@code sit} / {@code boat}
 * 沿用预览原值即可。这个结论是靠 <b>F3+B 碰撞箱</b>量出来的，不是靠目视试出来的。
 * <p>
 * 量法（可复现）：女仆碰撞箱是 {@code sized(0.6f, 1.5f)}，坐姿不变（{@code getDefaultDimensions}
 * 只对 SWIMMING 特判）。碰撞箱与模型在<b>同一深度平面</b>，透视对两者一视同仁，故近景也能量准：
 * 以碰撞箱宽 0.6 格定出「像素/格」，再量坐姿身体下缘到碰撞箱底面（＝实体 Y ＝地面）的距离。
 * 实测残差约 <b>0.03 格</b>，即预览原值已在误差带内。
 * <p>
 * ⚠️ <b>被推翻的假设</b>：中途曾据「三条都稍微陷进去」推断「女仆模型比玩家矮，
 * 模型根到座面的距离 D 随之变短，故每条都压过头」，并加过一个统一 trim（试过 0 / 0.05 / 0.1）。
 * 碰撞箱实测不支持它——那批「陷进去 / 偏高」的口头判读全落在 ±0.03 的噪声带里，
 * 被当成信号追了三轮。<b>D 确实是模型相关量，但本例中它并没有偏到需要补偿的程度。</b>
 * 谁要再动这几个数，先量再改，别照着目视描述二分。
 * <p>
 * ⚠️ 判读噪声带 ≈ ±0.03 格。这几个常量已经在带内，靠「感觉偏高/偏低」再调只会横跳。
 *
 * <h2>与 {@link MaidAnimationStates} / {@code LivingMovementAnimationPredicate} 的对应关系</h2>
 * 本类必须选中**实际在播的那条**动画，所以镜像了两处判据，**两边改动必须同步**：
 * <ul>
 *   <li>有载具 → {@code player.vehicle} 控制器的 {@code LivingMovementAnimationPredicate}
 *       （{@code MaidAnimationPredicate} 在有活载具时无条件 STOP，主表整个让位）；
 *       分支序逐条对齐，含 SWEM / 椅子 / 载具条件三条**优先于**内建载具的分支。</li>
 *   <li>无载具 → {@code player.main} 控制器的 {@link MaidAnimationStates}，只关心
 *       {@code sit}（HIGH，待命开关）；HIGHEST 那六条（death / sleep / swim / ladder×3）
 *       会抢在它前面，故必须一并排除，否则睡在床上的待命女仆会被压进床里 0.5 格。</li>
 * </ul>
 * 拿不准的一律返回 0：模型作者自定义的载具动画（SWEM / {@code ConditionVehicle} /
 * {@code ConditionChair}）重心未知，猜一个不如不动。
 * <p>
 * <b>TLM 坐垫 / 椅子 / 扫帚暂无常量</b>：{@code EntitySit}（挂点 −0.125）走的是
 * {@code MaidInteractionAnimHandler} 那批 gomoku / bookshelf / computer / keyboard /
 * picnic / chair / broom，不在预览覆盖的三种载具里，没有可复用的实测值，故返回 0
 * （＝维持现状）。要补就得实测，见 {@link #SEAT_UNMEASURED}。
 */
@Environment(EnvType.CLIENT)
public final class MaidPoseOffset {

    /**
     * 马等带鞍坐骑：{@code ride} 动画的模型根应在的高度（相对载具原点）。
     * <b>四条里唯一偏离预览原值的一条</b>：原值 0.85 实机偏低（人陷进马背），0.90 实测可用。
     */
    private static final float POSE_RIDE = 0.90f;

    /**
     * 猪：{@code ride_pig}。<b>四条里唯一没实机验证过的</b>——本值是拿 {@link #POSE_RIDE}
     * 的实测增量（+0.05）类推的，因为 {@code ride_pig} 与 {@code ride} 同属跨骑姿势。
     * 预览原值 0.3125。<b>要动它请先按类文档里的碰撞箱量法实测</b>，别照目视调。
     */
    private static final float POSE_RIDE_PIG = 0.3625f;

    /** 船：{@code boat}（负值——船的坐姿重心低于船体原点）。<b>沿用预览原值</b>，碰撞箱实测确认 */
    private static final float POSE_BOAT = -0.45f;

    /**
     * 待命坐姿：{@code sit}。无载具，实体 Y 就是地面，故常量直接就是修正量。
     * <b>沿用预览原值</b>，碰撞箱实测确认。
     */
    private static final float POSE_SIT = -0.5f;

    /** 无实测常量的座位（TLM 坐垫 / 椅子 / 扫帚 / 被玩家抱起）一律不动 */
    private static final float SEAT_UNMEASURED = 0.0f;

    private MaidPoseOffset() {
    }

    /**
     * @return 世界坐标系下应施加给模型根的 Y 修正量；0 表示「这个姿态没有已知的修正」
     */
    public static float resolve(EntityMaid maid, MaidAnimatable animatable) {
        Entity vehicle = vanilla(maid).getVehicle();
        if (vehicle == null || !vehicle.isAlive()) {
            return isStandbySitPlaying(maid) ? POSE_SIT : 0.0f;
        }
        return resolveSeated(maid, animatable, vehicle);
    }

    /**
     * 镜像 {@code LivingMovementAnimationPredicate.renderRidingAnimation} 的分支序。
     * 前三条命中即返回 0：那是模型作者自定义的动画，重心未知。
     */
    private static float resolveSeated(EntityMaid maid, MaidAnimatable animatable, Entity vehicle) {
        LivingEntity living = vanilla(maid);
        if (StringUtils.isNoneBlank(SWEMCompat.getHorseGaitName(living))) {
            return SEAT_UNMEASURED;
        }
        ConditionManager conditionManager = animatable.getModelConfig();
        ConditionChair conditionChair = conditionManager.getChair();
        if (conditionChair != null && StringUtils.isNoneBlank(conditionChair.doTest(living))) {
            return SEAT_UNMEASURED;
        }
        ConditionVehicle conditionVehicle = conditionManager.getVehicle();
        if (conditionVehicle != null && StringUtils.isNoneBlank(conditionVehicle.doTest(living))) {
            return SEAT_UNMEASURED;
        }
        if (vehicle instanceof Pig) {
            return POSE_RIDE_PIG - attachmentHeight(vehicle, living);
        }
        if (vehicle instanceof Mob mob && mob.isSaddled()) {
            return POSE_RIDE - attachmentHeight(vehicle, living);
        }
        if (vehicle instanceof Boat) {
            return POSE_BOAT - attachmentHeight(vehicle, living);
        }
        // 余下：carryon:princess（被玩家抱起）、TLM 坐垫/椅子/扫帚、以及兜底的 sit
        return SEAT_UNMEASURED;
    }

    /**
     * 载具把乘客的脚底原点抬到哪儿（相对载具自身原点）。
     * 与 {@code ModelPreviewRenderer.renderVehicleScenery} 里被否决的那个 yOffset 同一个量，
     * 只是这里是**减去**它而不是拿它当高度用。
     */
    private static float attachmentHeight(Entity vehicle, LivingEntity passenger) {
        return (float) (vehicle.getPassengerRidingPosition(passenger).y - vehicle.getY());
    }

    /**
     * 主表里 {@code sit}（HIGH）是否真的会被选中。
     * 逐条对齐 {@link MaidAnimationStates} 注册顺序里排在它前面的六个 HIGHEST 状态；
     * 少排除一条，那条状态生效时就会被错误地压低半格。
     */
    private static boolean isStandbySitPlaying(EntityMaid maid) {
        if (!maid.isMaidInSittingPose()) {
            return false;
        }
        LivingEntity living = vanilla(maid);
        return !living.isDeadOrDying()
                && living.getPose() != Pose.SLEEPING
                && !living.isSwimming()
                && !living.onClimbable();
    }

    /**
     * 把女仆当成 vanilla {@code LivingEntity} 看——理由同
     * {@link MaidAnimationStates}：Fabric 在开发环境重映射 mod jar 时，若调用点的 owner 是
     * 别的 mod 的类而方法其实继承自 vanilla，重映射器解析不出继承链，会把 intermediary 名原样
     * 留下，运行期炸 {@code NoSuchMethodError}。经 vanilla 静态类型调用后 owner 就是 vanilla 类。
     */
    private static LivingEntity vanilla(EntityMaid maid) {
        return maid;
    }
}
