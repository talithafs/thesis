# Environment Setup

I built this model with [Eclipse IDE for Eclipse Committers](https://www.eclipse.org/downloads/packages/release/oxygen/3a/eclipse-ide-eclipse-committers), Version: Oxygen.3a, Release 4.7.3a (Build id: 20180405-1200) and [Repast Simphony](https://repast.github.io/). In order to run it, you should:

1. Download and install *Eclipse Oxygen.3a*.
2. From within Eclipse, install Repast, following [these steps](https://repast.github.io/download.html) (section: *Repast Simphony Eclipse Update Site*). 
3. Import the Repast project contained here, under the `/abm` directory.
4. Press Eclipse's *Run* button. This will open the Repast view.
5. Press Repast's *Start* button.

**Tip**: If you have never worked with Repast, a good way to start is following [this](https://repast.github.io/docs/RepastJavaGettingStarted.pdf) tutorial. 

# The Model

The objective, the methodology, and a thorough description of the model can be found at [/initialization/paper.pdf](https://github.com/talithafs/abm-br/blob/master/initialization/paper/paper.pdf). This file is being constantly updated, so make sure you always have the latest version. Since the model is still under construction, several parts of this document may be outdated at any point in time, but I'll keep these marked in red. 

Why is this article draft in the `/initalization` folder? Because I use an *.rmd* file (`paper.rmd`) to generate it, and there you can also find the `R` code that calculates inputs to the Java program. 

# Directories

## /abm

Repast Simphony project (in Java). Folders were automatically generated by Eclipse. The most relevant among them are:

1. **src/abm**: Contains the model itself - agents, links (relationships), markets, etc.
2. **input**: Three *.csv* files that the Java program uses as input. Those were generated by [/initialization/paper.rmd](https://github.com/talithafs/abm-br/blob/master/initialization/paper/paper.rmd). 
    * *dist_fir.csv*: Distribution of firms by size (number of employees)
    * *dist_inc.csv*: Distribution of income among consumers
    * *parameters*: Other parameters of the model, such as GDP, number of agents, etc.
3. **output**: Each .csv contains the series that are necessary to calculate a given stylized fact about general economies. Two words of caution: (i) I delete these files regularly, and (ii) I'm not yet entirely sure the series are being correctly calculated. 
4. **tests**: Unit tests - some of them are outdated, because they were designed for earlier versions of the model
5. 
