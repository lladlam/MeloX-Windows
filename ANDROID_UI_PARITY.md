# Android UI Parity — MeloX-Windows

Android `main` 分支是唯一 UI/UX Source of Truth。
每修复一项就更新此清单。状态：☐ 未开始 / ◐ 进行中 / ☑ 与 Android 一致

## App Shell
| 项 | Android 源 | Windows 源 | 状态 |
|---|---|---|---|
| AppTab 体系 | ui/MeloXApp.kt | ui/navigation/AppRoute.kt | ☑ |
| rememberSaveableStateHolder | MeloXApp.kt | MeloXApp.kt | ☑ |
| tabBarMinimized + scrollAccumulator ±18px | MeloXApp.kt:305-335 | MeloXApp.kt | ☑ |
| SeekableTransitionState player | MeloXApp.kt | MeloXApp.kt | ◐ (无手势 seek) |
| Content fade 320/240 | MeloXPageTransitions.kt | MeloXMotion.kt | ☑ |
| Back 行为（返回 Home / 双击退出） | MeloXApp.kt:470-505 | ✗ 无键盘 back 语义 | ☐ |

## Bottom Chrome
| 项 | Android 源 | 状态 |
|---|---|---|
| smoothStep 四阶段 (label .32/size .36/shrink .82/drop 1.0) | MeloXApp.kt:889-1218 | ☑ |
| DampedDock (78/56 press, segment tap, drag select) | PublicDampedDragAnimation.kt | ☑ |
| Selection lens sibling overlay + velocity stretch | MeloXApp.kt | ◐ (无 lens refraction 光学畸变) |
| Liquid bar (blur 8dp, lens 24/28, highlight .32/.54+press) | MeloXBackdropComponents.kt | ◐ (无真实 backdrop 采样) |
| chromeHeight 124/64 → 56 | MeloXApp.kt | ☑ |
| miniLift 66dp × mediaReveal | MeloXApp.kt | ☑ |
| Tab 图标映射 (Podcasts→RadioWaves, Cloud→Storage) | MeloXApp.kt:1323-1326 | ☑ |
| Icon Fill variant（选中态实心） | MeloXSymbolVariant.Fill | ☐ (需 fill 字体) |

## Mini Player
| 项 | Android 源 | 状态 |
|---|---|---|
| 52dp capsule, White@6% glass | MeloXIOSMiniPlayer.kt | ☑ |
| artwork 40→30dp, corner 6dp | MeloXIOSMiniPlayer.kt | ☑ |
| title 14sp/17 SB, artist 12sp α0.64, height 15→0 | MeloXIOSMiniPlayer.kt | ☑ |
| controlStage 72→36dp (ss .08/.84) | MeloXIOSMiniPlayer.kt | ☑ |
| chrome/surface alpha 阶梯 | MeloXIOSMiniPlayer.kt | ◐ (compactProgress 代理) |
| 横滑切歌 ±28px, spring .68/360, 1.5s 保护 | MeloXIOSMiniPlayer.kt | ☑ (无队列，回弹) |
| DancingBars 15×18dp 音频反应 | MeloXAudioReactiveRuntime | ◐ (合成 wobble) |
| sharedBounds shell→player | MeloXSharedTransitions.kt | ☐ |

## Full Player
| 项 | Android 源 | 状态 |
|---|---|---|
| 279dp controls (52+19+82+31+42+3+50) | MeloXNowPlayingCoreControls.kt | ☑ |
| artwork scale 0.74 springs (grow .70/280, shrink .94/360) | MeloXNowPlayingCoreControls.kt | ☑ |
| shadow 26/14 spring .92/320 | MeloXNowPlayingCoreControls.kt | ☑ |
| play/pause swap (fadeIn 180/scaleIn .78/slideIn 24%) | MeloXNowPlayingCoreControls.kt | ☑ |
| 页面 cross-fade spring(.7,300), lyrics/queue ±400dp | MeloXIOSNowPlayingScene.kt | ◐ (无 scale 0.92) |
| Lyrics 引擎（逐行高亮/翻译/罗马音） | MeloXIOSLyricsPanel.kt | ☐ (暂无歌词) |
| Queue 完整列表 + 拖拽重排 | MeloXQueuePanel.kt | ☐ |
| 手势下拉关闭 (grabber drag) | MeloXIOSNowPlayingScene.kt | ◐ (tap only) |
| seek on drag + settle spring(1.0,420) | MeloXApp.kt:677-694 | ☐ |
| 音质选择 / actions sheet | MeloXQualitySelectionOverlay.kt | ☐ |

## Glass
| 项 | Android 源 | 状态 |
|---|---|---|
| MeloXGlassTokens (SystemColors/Spec/Typography) | MeloXGlassTokens.kt | ☑ |
| Liquid Button (blur 2/lens 24/refraction 12) | MeloXBackdropComponents.kt | ◐ (无 lens 畸变) |
| Liquid BottomBar (blur 8, lens 24/28, highlight) | MeloXBackdropComponents.kt | ◐ |
| Liquid TabSelection (velocity stretch) | MeloXBackdropComponents.kt | ◐ |
| Layer backdrop 真实采样 (rememberLayerBackdrop) | Kyant backdrop | ☐ (桌面无等价 API) |
| vibrancy + chromatic aberration | Kyant backdrop | ☐ |
| GlassSheet / GlassDialog / GlassButton | MeloXGlassSheet.kt / NativeGlassComponents.kt | ◐ (简化版) |
| SettingsDropdown / SwipeActionRow / LiquidSlider | glass/ | ☐ |

## Screens
| 页面 | Android 源 | 布局 | 数据 | 状态 |
|---|---|---|---|---|
| Home | discovery/MeloXDiscoveryScreens.kt:187-718 | ☑ | ◐ (homeFeed QQ/Kugou; Netease 缺) | ◐ |
| Explore | discovery:720-915 | ◐ | ◐ | ◐ |
| Library | library/LibraryScreen.kt | ☐ | ☐ | ☐ |
| Search | search/SearchScreen.kt | ☐ | ◐ | ☐ |
| Settings | settings/ | ◐ | ✗ mock | ☐ |
| Podcasts | podcast/MeloXPodcastScreen.kt | ☐ | ✗ mock | ☐ |
| Downloads | LibraryScreen(forcedPage) | ☐ | ✗ mock | ☐ |
| Cloud | cloud/MeloXCloudMusicScreen.kt | ☐ | ✗ mock | ☐ |
| Messages | messages/MessagesScreen.kt | ☐ | ✗ mock | ☐ |
| Login | account/NeteaseLoginScreen.kt | ☐ | ✗ | ☐ |
| AlbumDetail | collection/ | ☐ | ✗ | ☐ |
| PlaylistDetail | library/MeloXUnifiedPlaylistDetailScreen.kt | ☐ | ✗ | ☐ |
| Provider 页面 | provider/ | ☐ | ✗ | ☐ |
| Messages slide-in 300ms | MeloXApp.kt:614-630 | ☐ | — | ☐ |

## Symbols
| 项 | 状态 |
|---|---|
| 码点表 0x100xxx 与 Android 一致 | ☑ |
| MeloXSymbol 全量对齐 (Android 66 项) | ◐ (约 60 项) |
| Fill variant 字体 (SF Pro Fill) | ☐ |
| 动态字形切换动画 | ☐ |

## Typography
| 项 | 状态 |
|---|---|
| MiLanPro/SF Pro 字体加载 | ☑ |
| iOS 字号阶梯 largeTitle→caption | ☑ |
| Monospace 进度标签 11sp α0.50 | ☑ |

## 截图验收
| 状态 | 状态 |
|---|---|
| Android .adb_compare 参照归档 | ☑ (96 文件) |
| Windows 1280×2772 参考窗口 | ☐ |
| 截图 overlay 差异对比 | ☐ |
| 动态交互对比（滚动/切页/展开） | ☐ |

## 已知平台限制（不计入验收，但需最接近替代）
1. **Layer backdrop 采样**：Android Kyant 库用 RenderNode 记录页面层做实时 blur+lens。Compose Desktop 无等价 API，当前用固定半透明层模拟。可调研 Skia `ImageFilter.makeBlur` + `saveLayer` 离屏采样实现。
2. **AGSL RuntimeShader**：lens 色差用径向渐变替代。
3. **音频反应采样**：MeloXAudioReactiveRuntime 未移植，DancingBars 用合成 wobble。
