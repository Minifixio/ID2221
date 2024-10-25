# What to put in the report ?

## Introduction (Anna)
- Explain how we did select the dataset, describe how the data is arranged in the dataset (different fields in the JSON).
    - Explain the source of the paper (AMiner Citation https://www.aminer.cn/citation) and what those papers are dealing with (sources from mostly Computer Science conferences)
    - Describe how the JSON objects are formed (the different fields) and what are the relevant ones for our computations (the paperId, references list, and maybe the authors if we want to analyse papers based on auhors):
 
 Paper format :
 ```json
 |-- id: string (nullable = true)
 |-- title: string (nullable = true)
 |-- year: long (nullable = true)
 |-- abstract: string (nullable = true)
 |-- authors: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- id: string (nullable = true)
 |    |    |-- name: string (nullable = true)
 |    |    |-- org: string (nullable = true)
 |-- keywords: array (nullable = true)
 |    |-- element: string (containsNull = true)
 |-- doc_type: string (nullable = true)
 |-- doi: string (nullable = true)
 |-- fos: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- name: string (nullable = true)
 |    |    |-- w: double (nullable = true)
 |-- indexed_abstract: string (nullable = true)
 |-- isbn: string (nullable = true)
 |-- issn: string (nullable = true)
 |-- issue: string (nullable = true)
 |-- lang: string (nullable = true)
 |-- n_citation: long (nullable = true)
 |-- page_end: string (nullable = true)
 |-- page_start: string (nullable = true)
 |-- references: array (nullable = true)
 |    |-- element: string (containsNull = true)
 |-- url: array (nullable = true)
 |    |-- element: string (containsNull = true)
 |-- venue: struct (nullable = true)
 |    |-- raw: string (nullable = true)
 |-- volume: string (nullable = true)
```

## Code analysis (Emile)
### Emile:
- Describe how did we decided to parse the JSON : taking the paper ID as ndoe ID, taking the references field list of each article to create the links between the nodes.
- Explain the methodology in Spark to read the JSON and build the graph with VertexRDD and EdgeRDD (details the `map`, `filter`, `reduceByKey` transformations...) and how did we built the graph by keeping only deges that links nodes that are actually in the dataset.
- Explain how did we add the functionnality to compute connected component graph.

## Visualization (Emile)
- Explain how to we designed the different export functionalities (JSON & CSV) and how to do it efficiently with `mapPartitions` for instance and how the CSV format can be used on [https://cosmograph.app/](https://cosmograph.app/) to visualize data.

## Problems encountered (Emile)
- Explain how we actually split the file into smaller JSON files because it was too big to compute on a single personnal computer instance and how we did use HDFS to store the file and access it. Explain also the problems encountered with the maximum size of the memory heap and how we fixed it using (`export SBT_OPTS="-Xmx8g -XX:+UseG1GC"`).

## Quick conclusion and possible improvements (Emile)
- State what could have been done more and the limitations of the actual data