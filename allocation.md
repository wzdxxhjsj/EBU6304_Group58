A：登录 / 角色入口

负责内容

Welcome Page
角色选择（TA / MO / Admin）
ID + password 登录校验
登录后跳转到对应 dashboard
权限限制（不同角色不能直接进别人的页面）

jialuning/feature/login-auth


B：TA-1（岗位浏览与申请）

负责内容

TA Dashboard
岗位列表展示
module 搜索 / workload filter
Position Detail
Apply 流程
adjustment yes/no
CV 上传入口
最多申请4个，但录取最多录取3个

这些都是原型里 TA 主流程的一半。

对应 Iteration

I1 必须完成：岗位列表、详情、申请
I2 再补强：搜索筛选体验、重复申请拦截、关闭岗位后禁申

依赖

依赖 A 的登录
依赖 MO 侧先有 module 数据结构

yangxiaorui/feature/ta-browse-apply


C：TA-2（Profile 与 Application History）

负责内容

TA Profile 页面
profile 发布/保存
CV 文件路径绑定
Application History / Status 页面
已申请列表与状态刷新
显示 Submitted / Accepted / Rejected / Waiting for Assignment


对应 Iteration

I1 必须完成：Profile + Application History
I2 再补强：页面联动、状态文案、已录取数显示

依赖

依赖 A 的登录
依赖 B 已经把申请写入
依赖 MO 侧能更新申请状态

lijunyue/feature/ta-profile-history


D：MO-1（MO Dashboard 与审核）

负责内容

MO Dashboard
查看自己模块
进入模块申请详情页
查看申请者
Accept / Not Accept
录满 3/3 后禁录

这些是 MO 最核心的业务闭环。原型里模块详情页已经把规则写得很清楚。

对应 Iteration

I1 必须完成：查看申请 + accept/reject
I2 补强：状态联动更完善、录满逻辑、防重复录取

依赖

依赖 A 的登录
依赖 TA 侧先产出申请数据
依赖 TA profile 先能被查看

wuxuan/feature/mo-dashboard-review

E：MO-2（岗位发布 / 编辑 / 关闭）

负责内容

New Module Publishing Page
发布岗位
Edit 模块信息
Close job
关闭后不可申请
MO 页面部分数据管理逻辑


对应 Iteration

I1：先把模块数据结构和初始模块展示配合好
I2：重点完成发布、编辑、关闭

依赖

依赖 A 的登录
和 D 强绑定，需要共用 module 数据结构
F：Admin（Dashboard + Reassign）

liangyuruo/feature/mo-publish-edit-close


F.admin
负责内容

Admin Dashboard
Finished / Unfinished 课程状态
TA application dashboard
Waiting for adjustment 列表
Reassign page
Reject page / 操作
workload / available positions 聚合展示


对应 Iteration

I1：先不重做太多，只准备数据结构与简单 dashboard 壳子
I2：主攻 dashboard + reassign

依赖

依赖 A 的登录
依赖 TA 侧有 profile / application
依赖 MO 侧有 module 状态、accept/reject 结果
是最靠后的模块
qin/feature/admin-dashboard-reassign