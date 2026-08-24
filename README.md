# RedPacket2
![image](REDPACKET2.png)  
想要在你的服务器上发红包吗？   
试试这个插件！  

# 链接
- [MCBBS](https://www.mcbbs.co/thread-3152-1-1.html)
- [Modrinth](https://modrinth.com/project/redpacket2)
- [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/redpacket2)

# 要求
需要 [Vault](https://www.spigotmc.org/resources/vault.34315/) 以及 经济插件（如XConomy）。   
将JAR文件拖放到`plugins`文件夹，然后重启服务器。  

# 功能  
**红包类型**   
它可以设置红包的类型，如：普通红包、成语红包、口令红包，   
对于普通的红包，你只需要点击聊天框中的提示来领取，   
接龙红包要求玩家说出对应成语来领取，   
口令红包要求玩家说出相应的口令来领取。  

**给予类型**   
有两种给予类型：一种是运气红包，一种是固定红包。  

**红包信息**   
普通的红包仅需设置祝福语，   
接龙红包需要设置接龙的成语，   
口令红包需要设置对应的口令。  

**领取人**   
点击聊天框中的收取人，在聊天框中输入玩家名字（以“，”分隔）  

**红包的金额和数量**   
在聊天框设置即可。  

**最后一步**   
点击聊天框中的 **创建** 发送红包   
点击聊天框中的 **取消** 可以取消红包  

# 用法  
- `/rp add/new` - 创建新的红包  
- `/rp reload` - 重新加载插件  

# 权限
|               权限               |        描述        |    默认    |
| ------------------------ | --------------- | --------- |
| redpacket.admin         | 管理员权限     | op          |
| redpacket.user             | 玩家权限        | false       |
| redpacket.command.* | 插件指令权限 | op/false |
| redpacket.get.*            | 获取红包        | false       |
| redpacket.set.*             | 设置红包        | false       |

# 配置  
配置文件：`plugins/RedPacket/config.yml`  
- `Database-Type` 数据库类型，支持MySQL和Sqlite  
- `RedPacket-MaxAmount` 最大红包数量  
- `RedPacket-MaxMoney` 红包最大金额  
- `RedPacket-MinMoney` 红包最小金额  
- `RedPacket-Expired` 红包是否自动过期  

# 关于RedPacket插件
我并不是这个插件的原作者，我主要是把这个插件支持到了1.21-1.21.4版本   
原作者的Github：[sandtechnology](https://github.com/sandtechnology/RedPacket)   
注意：原作者已停止更新此插件！   
- 下载RedPacket插件的**1.8-1.19.3**版本，请点击链接 [Minebbs - RedPacket](https://www.minebbs.com/resources/redpacket-x-x.9017/)  
- 下载RedPacket2插件的**1.21-26.2**版本，就是你在看的这个仓库，前往 [Releases](https://github.com/xiaomian124/RedPacket2/releases)下载
 
# 报告问题  
因为我是Java的初学者，插件部分代码制作依赖于AI，请理解！   
如果您有任何问题反馈或者建议，请打开 [Issue](https://github.com/xiaomian124/RedPacket2/issues) 告诉我，我尽量解决。

# 许可证  
此插件尚未获得原作者的许可！  
要了解更多信息，请参考：[许可证](https://github.com/xiaomian124/RedPacket2/blob/main/LICENSE)、[许可证](https://github.com/xiaomian124/RedPacket2/blob/main/LICENSE.txt)  
