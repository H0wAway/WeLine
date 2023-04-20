# WeLine (DEMO_2.0)
## 基于NIO的简单聊天系统

# 一、功能

## （1）服务端

1. 接收客户端连接

2. 用户信息验证

3. 接收客户端消息并存储，进行消息转发

4. 消息记录存储和查询

## （2）客户端




|             指令             |   功能   |
|:--------------------------:|:------:|
|           /help            | 查看指令集  |
|           /login           |   登录   |
|          /signup           |   注册   |
|          /logoff           |   注销   |
|      /chat &lt;user1>      |  开启私聊  |
|   /group &lt;groupName>    |  创建群聊  |
| /add &lt;user1> &lt;user2> |  邀人加群  |
|           /exit            |  退出聊天  |
|          /cancel           |  删除群聊  |
|          /history          | 查看历史记录 |



# 二、实现思路

## 1. 传输方式：NIO实现TCP传输

## 2. 数据格式：

* Message类` = date + Source + Target + data;`
专门处理信息
* GroupUsersInfo类` = userName + isOnline + lastOnline;`
存储用户的状态
* groupMap表`Map<String, List<GroupUsersInfo>>` ：存储群名和该群的所有用户信息

## 3. 服务器：只创建一个线程用以监听客户端信息，根据信息头作不同处理。
chat/group进入群组，该群组记录每个用户userName  
exit退出之后，群组记录最后登录时间lastOnline  
// java.util.Date类实现了Comparable接口，可以直接调用date1.compareTo(date2)来比较大小    
// date1小于date2返回-1，date1大于date2返回1，相等返回0  
被迫加入群组时，记录当前时间，等他自己再进来时，才发消息。  
不打开，永远不知道有人给自己发了消息。  

## 4. 客户端：共有两个线程，一个线程用来监听服务器发来的信息，一个线程用于从控制台读取输入。

状态码：判断用户的非指令输入(不带'/'') 的状态

| statusCode |     状态      |  
|:----------:|:-----------:|  
|     0      |     未登录     |
|     1      |    正在登录     |
|     2      |     已登录     |
|     3      | 注册——正在输入用户名 |
|     4      | 注册——正在输入密码  |
|     5      | 注册——正在确认密码  |




# 三、TODO
1. /group权限：不在group中，不能随意进入。
2. /add权限：只有群主可以add
3. 加入group时，根据lastOnline比较，显示自己不在群里时大伙聊了些什么。

# 四、ISSUE
1. 数据结构用“|”隔开，一旦用户输入‘|’基本就GG
2. 客户端异常（直接关闭、直接注销）时，服务器如何正确判断且修改group中该用户的状态信息!!!





    