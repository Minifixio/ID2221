object Main {

  def hdfsInputPath = "hdfs://localhost:9000/user/emile/dblp_v14-part2-3.json"
  def hdfsOutputPath = "data/graph-export"

  def main(args: Array[String]): Unit = {
    println("Starting to build the graph...")
    var (graph, validVertexIds, componentPapersMap) = BuildGraph.build(hdfsInputPath, BuildGraph.ConnectedComponentsGraph, 50, 1)
    println("Graph built successfully!")
    ExportGraph.exportGraph(graph, validVertexIds, hdfsOutputPath, ExportGraph.JSONFormat, true, componentPapersMap)
    println("Graph exported successfully!")
  }
}
