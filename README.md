# ID2221 : Setup help

## 1. Setting up HDFS on MacOS
### Step 1: Get Homebrew
You can find instrucitons to get it at [https://brew.sh/](https://brew.sh/).

Then run :
```bash
brew update
```

### Step 2: Setup Java
You must use Java 8.

Run
```bash
java -version
```

If you have an error or if you don't have something like :
```bash
openjdk version "1.8.0_292"
OpenJDK Runtime Environment (AdoptOpenJDK)(build 1.8.0_292-b10)
OpenJDK 64-Bit Server VM (AdoptOpenJDK)(build 25.292-b10, mixed mode)
```
with `1.8` version then follow those steps :


```bash
brew install - cask homebrew/cask-versions/adoptopenjdk8
````

Add this to your `bash_profile` (if you use bash) or `.zprofile` (if you use zsh). 

```bash
export JAVA_HOME=$(/usr/libexec/java_home)
```

Example : 
1. `nano ~/.bash_profile`
2. Add `export JAVA_HOME=$(/usr/libexec/java_home)``
3. Save with `Ctrl+O``
4. Save changes with `source ~/.bash_profile`

See [this](https://stackoverflow.com/questions/30461201/how-do-i-edit-path-bash-profile-on-os-x) for help in case.

<br>

Now if you run 
```bash
java -version
```
you should see `1.8` version.

### Step 3: Install Hadoop 3.3.6/3.4.0 using homebrew
Run
```bash
brew install hadoop
```

And check your version :
```bash
hadoop version
```

Now, move to hadoop directory and verify the files/path :
```bash
cd /opt/homebrew/Cellar/hadoop/3.4.0
```


### Step 4: Updated Hadoop config files -> 5 files
Go to directory :
```bash
cd /opt/homebrew/Cellar/hadoop/3.4.0/libexec/etc/hadoop
```
Open this directory in any code editor of choice.

#### Get your java SDK path 
Run
```bash
/usr/libexec/java_home
```
and get the path

Example :
```bash
(base) MBP-Emile :: ‹main*› % /usr/libexec/java_home

/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home
```

the path is `/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home`

#### Edit `hadoop-env.sh`
Add the line
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home
```
with the previous path you got

#### Edit `core-site.xml`
Must be :
```xml
<configuration>
  <property>
      <name>fs.defaultFS</name>
      <value>hdfs://localhost:9000</value>
      <final>true</final>
  </property>
</configuration>
```

#### Edit `hdfs-site.xml`
Must be :
```xml
<configuration>
   <property>
      <name>dfs.replication</name>
      <value>1</value>
   </property>
</configuration>
```

#### Edit `mapred-site.xml`
Must be :
```xml
<configuration>
  <property>
    <name>yarn.app.mapreduce.am.env</name>
    <value>HADOOP_MAPRED_HOME=/opt/homebrew/opt/hadoop</value>
  </property>
  <property>
    <name>mapreduce.map.env</name>
    <value>HADOOP_MAPRED_HOME=/opt/homebrew/opt/hadoop</value>
  </property>
  <property>
    <name>mapreduce.reduce.env</name>
    <value>HADOOP_MAPRED_HOME=/opt/homebrew/opt/hadoop</value>
  </property>
</configuration>
```

#### Edit `yarn-site.xml`
Must be :
```xml
<configuration>
   <property>
      <name>yarn.nodemanager.aux-services</name>
      <value>mapreduce_shuffle</value> 
   </property>
</configuration>
```

### Step 5: Start hadoop
Try running :
```
start-all.sh
```

if you have a `localhost connection error` or something like this :
#### 1. Stop hadoop
```bash
stop-all.sh
```

#### 2. Authorize remote connection on MacOS
See [this link](https://support.apple.com/fr-fr/guide/mac-help/mchlp1066/mac) to do it.

#### 3. If you don't have public rsa key do
```bash
ssh-keygen -t rsa -P '' -f ~/.ssh/id_rs
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
```

#### 4. Restart Hadoop
```bash
hadoop namenode -format
start-all.sh
```

Now you can visit `http://localhost:9870` to check the Hadoop interface.


### Step 6: Add files to HDFS
Create the `/data` directory
```
hdfs dfs -mkdir -p /data 
```

Then go to `id2221-scala/data` and copy file :
```bash
hdfs dfs -put [FILE_NAME].json data/[FILE_NAME].json  
```

## 2. Setup the notebook
### Step 1: Install Jupyter
See [https://jupyter.org/install](https://jupyter.org/install).

### Step 2: Install the spylon kernel
Run 
```bash
pip install notebook
```

```bash
pip install spylon-kernel
```

```bash
python -m spylon_kernel install
```

### Step 3: Run the notebook
Go to the `id2221-notebooks` folder and then run :
```bash
jupyter notebook
```

Now you can choose spylon kernel in the kernel list in the notebook.

## 3. Setup the scala project
See [https://docs.scala-lang.org/getting-started/index.html](https://docs.scala-lang.org/getting-started/index.html) to setup a Scala project.

Then you can go to the `id2221-scala` folder and run
```bash
sbt run
```

Make sure to change the input and output file path in the `id2221-scala/src/main/scala/Main.scala` file if you want to :

```scala
object Main {

  def hdfsInputPath = "hdfs://localhost:9000/data/dblp_v14-part2266-3.json"
  def hdfsOutputPath = "data/graph-export.json"

  def main(args: Array[String]): Unit = {
    println("Starting to build the graph...")
    var (graph, validVertexIds) = BuildGraph.build(hdfsInputPath)
    println("Graph built successfully!")
    ExportGraph.exportGraph(graph, validVertexIds, hdfsOutputPath)
    println("Graph exported successfully!")
  }
}
```




