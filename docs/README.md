# mealnote 代码级知识库(docs/)

> 本目录是食单（曾用名 味笺 / 饭记）项目的**代码级参考知识库**,由逐行阅读源码整理,为持续开发提供可复用上下文。
> 稳定协作规约见根目录 `../AGENTS.md`;产品/视觉/规划以根目录既有文档为权威(`README.md`、`PROJECT_OVERVIEW.md`、`DESIGN_SYSTEM.md`、`ROADMAP.md`、`CHANGELOG.md`)。

## 文档地图

| 文档 | 内容 | 何时看 |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 分层数据流、应用入口与 DI、导航路由表与导航流、状态管理模式、包结构地图 | 想了解整体骨架、加新页面/路由 |
| [DATA_LAYER.md](DATA_LAYER.md) | Repository/Dao/DB/迁移、PhotoStore/BackupStore/WebDavStore、AppLog、金额与文本工具、设置偏好、数据流与约束速查 | 改数据模型、事务、文件生命周期、迁移 |
| [UI_LAYER.md](UI_LAYER.md) | 主壳与底部导航、首页域(清单/足迹/统计/账本/随机)、表单/详情域、组件体系、主题/玻璃/动效 | 改界面、组件、算法逻辑 |
| [FEATURES.md](FEATURES.md) | 以功能为主线串起数据+界面:记账、随机、备份、WebDAV、分享、日志、流畅模式等,附功能↔代码对照 | 按功能定位涉及的所有代码 |
| [BUILD_AND_TEST.md](BUILD_AND_TEST.md) | 构建命令、中文路径与 JDK 坑、JDK 直跑测试、测试清单与策略、发布门槛 | 构建、跑测试、加测试 |
| [SECURITY_PRIVACY.md](SECURITY_PRIVACY.md) | 权限、数据安全四不变式、AppLog 隐私红线、WebDAV/备份/照片安全、签名与密钥 | 涉及权限/网络/删除/日志/发布 |

## 快速起步(接手项目)

1. 先读 `../AGENTS.md`(硬约定)与本页;
2. 按任务方向选上表文档深入;
3. 动手前确认 `docs/BUILD_AND_TEST.md` 的环境与命令(中文路径联接 + JDK 17 + JDK 直跑测试);
4. 涉及数据模型/删除/网络/日志时,先核对 `docs/SECURITY_PRIVACY.md` 的不变式再改。

## 维护约定

- 这些文档描述**当前代码事实**(截至 DB v4 / v0.5.1)。改动相关代码时同步更新对应文档;
- schema/迁移/测试清单变化必须同步 `README.md` 与 `docs/DATA_LAYER.md`、`docs/BUILD_AND_TEST.md`;
- 文档只写稳定事实与约束,不写临时状态与凭据。
