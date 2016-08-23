# AlxGifHelper
一个自定义GIF播放的管理类，基于github上著名项目android-gif-drawable制作，实现了下载进度提示，第一帧占位显示，磁盘缓存和LRU缓存等功能
显示效果和思路介绍请见：http://blog.csdn.net/lvshaorong/article/details/51732520

需要添加多种CPU型号支持的so库请修改以下文件
AlxGifHelper\libraries\android-gif-drawable\src\main\jni\Application.mk
中APP_ABI := armeabi x86
使用NDK11及以上可添加64位arm，x86和mipsCPU的支持

效果展示：

![demo](https://github.com/AlexZhuo/AlxGifHelper/blob/master/gif_demo1.gif)
![demo](https://github.com/AlexZhuo/AlxGifHelper/blob/master/gif_demo2.gif)
