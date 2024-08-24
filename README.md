# TwitterBanSpamFollowers

#### 欢迎使用 TwitterBanSpamFollowers 应用程序！

#### 此工具将通过一定的规则Block用户，提高互动质量。减少各种广告、无意义账号的关注以及机器账号的监视。

## 🛠️ 安装

首先，确保您的机器上已安装 Java。您可以从 [Oracle](https://www.oracle.com/java/technologies/javase-jdk21-downloads.html)
下载，或者使用您喜欢的包管理器，例如：

### **Homebrew：**

```bash
brew install openjdk
```

### **APT：**

```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

### **YUM：**

```bash
sudo yum install java-21-openjdk-devel
```

### **Pacman：**

```bash
sudo pacman -S jdk21-openjdk
```

## 🚀 快速开始

### 1. **Twitter 进行身份验证：**

首先，运行以下命令以使用 Twitter 进行身份验证：

   ```bash
   java -jar app.jar auth
   ```

这将弹出一个链接，用它打开一个网页，您可以在其中登录到您的 Twitter 帐户并输入提供的 PIN 码。

此过程将在您的目录中生成一个`twitter_credentials.properties` 文件。

### 2. **Run命令：**

例如，要Block使用默认个人资料图片、在过去 3 个月内注册且关注者与被关注者比例大于 20:1 的用户，请使用以下命令：

   ```bash
   java -jar app.jar run --picture --register 3 --ratio 20
   ```

要查看运行命令的所有可用选项，请使用：

    ```bash
    java -jar app.jar run --help
    ```

这将显示可用的用法和选项，您可以将它们组合使用。

   ```bash
    Usage: <FileName.jar> run [<options>]

    Block them now!

    Options:
      --access-token=<key>   OAuth access token
      --access-secret=<key>  OAuth access token secret
      --dry-run              Print destructive actions instead of performing them
      --picture              Block users with default profile pictures
      --register=<int>       Block users registered within the specified number
                            of months
      --spam=<int>           Block users with 0 fans but too many followings
      --locked               Block users who have protected their tweets
      --include-site         Also scan the user's website link
      --include-location     Also scan the user's location string
      --ratio=<int>          Block users with a followings-to-followers ratio
                            higher than the specified value
      -h, --help             Show this message and exit

   ```

**若命令后跟随`<int>`则代表需要跟随数字，如`--ratio=<int> `，需要使用`--ratio 5`等。**

其中`--access-token=<key>`和`--access-secret=<key>`已经在**Twitter 进行身份验证**步骤获得，将根据目录下的缓存文件
`twitter_credentials.properties`读取，不需要额外指定。

### 3. **Execute命令：**

此命令将根据 `users_to_block.json` 文件开始Block其中的用户。

```bash
java -jar app.jar execute
```

## <span style="color:red">特别提示：您有义务在执行最终Block前进行手动的检查，确认每一位用户是否真的需要被Block，而不是
*仅* 靠程序代工。由于您的疏忽导致的任何后果与作者无关。</span>

## ⚙️ 配置

### **0.检查哪里的关键词？**

默认情况下，会检查用户的`screenName`，即昵称。以及`description`，即用户简介。

当然，在启用了` --include-location`或（和）` --include-site `的时候，也会相应的检查用户地址栏以及用户网站栏填写的内容。

### **1.内置敏感词**

程序内置了敏感词列表。文件源于[sensitive-word项目](https://github.com/houbb/sensitive-word)
的 [中文敏感词字典](https://github.com/houbb/sensitive-word/blob/master/src/main/resources/sensitive_word_dict.txt)。存储在
`src/main/resources/sensitive_word_dict.txt`路径下。您可以根据您的喜好更换、添加、删改。

### **2.用户敏感词**

您可以在程序内置的敏感词列表的基础上设置用户敏感词。它在当前目录下的`blocking_rules.json`文件中定义。下面是它的默认格式：

```json
{
    "userKeywords": [],
    "excludeKeywords": []
}
```

顾名思义，`userKeywords`就是您要在内置敏感词的基础上额外新加的敏感词列表,则是排除项。有时候，并不能根据设置的敏感词很好的区分一个人是否需要block，
`excludeKeywords`则在这时候派上了用场。

例如，在假设内置敏感词文件中**没有**`crypto`这一项。那么，在如下设置中，就会考虑block包含敏感词`crypto`的人。但是假设他同时又包含关键词
`男高`，则不会被block。

```json
{
    "userKeywords": [
        "crypto"
    ],
    "excludeKeywords": [
        "男高"
    ]
}
```

最终结果是，所有包含`crypto`的人都会被block，**但是包含`男高`的那部分例外。**