package technology.tabula.extractors;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import technology.tabula.Cell;
import technology.tabula.Page;
import technology.tabula.Rectangle;
import technology.tabula.RectangleSpatialIndex;
import technology.tabula.Ruling;
import technology.tabula.Table;
import technology.tabula.TableWithRulingLines;
import technology.tabula.TextElement;
import technology.tabula.Utils;

/**
 * @author manuel
 *
 */
public class SpreadsheetExtractionAlgorithm implements ExtractionAlgorithm {
    
    private static final float MAGIC_HEURISTIC_NUMBER = 0.65f;

    // Adjacency tolerance shared by the spatial-index pre-filter and the exact edge-alignment check;
    // the two must stay in sync, otherwise the pre-filter could miss neighbors within tolerance
    // 相邻判定容差：空间索引预筛与精确边对齐判定共用；两者须保持一致，否则预筛会漏掉容差内的相邻单元格
    private static final double ADJACENCY_GAP = 2 * Utils.EPSILON;
    
    private static final Comparator<Point2D> Y_FIRST_POINT_COMPARATOR = (point1, point2) -> {
        int compareY = compareRounded(point1.getY(), point2.getY());
        if (compareY == 0) {
            return compareRounded(point1.getX(), point2.getX());
        }
        return compareY;
    };
    
    private static final Comparator<Point2D> X_FIRST_POINT_COMPARATOR = (point1, point2) -> {
        int compareX = compareRounded(point1.getX(), point2.getX());
        if (compareX == 0) {
            return compareRounded(point1.getY(), point2.getY());
        }
        return compareX;
    };

    private static int compareRounded(double d1, double d2) {
        float d1Rounded = Utils.round(d1, 2);
        float d2Rounded = Utils.round(d2, 2);

        return Float.compare(d1Rounded, d2Rounded);
    }

    // Find a point within EPSILON tolerance by scanning the accumulated points in forward order
    // 正序遍历已累计的点，查找在 EPSILON 容差范围内的点
    private static Point2D findPointByFeq(List<Point2D> points, Point2D target) {
        int size = points.size();
        for (int i = 0; i < size; i++) {
            Point2D p = points.get(i);
            if (Utils.feq(p.getX(), target.getX()) && Utils.feq(p.getY(), target.getY())) {
                return p;
            }
        }
        return null;
    }
    
    @Override
    public List<Table> extract(Page page) {
        return extract(page, page.getRulings());
    }
    
    /**
     * Extract tables from a PDF page using ruling lines as cell boundaries
     * 使用标线作为单元格边界从PDF页面中提取表格
     *
     * This method processes ruling lines to identify and extract structured tables:
     * 该方法通过处理标线来识别和提取结构化表格：
     * 1. Separate rulings into horizontal and vertical lines, then collapse overlapping lines
     * 1. 将标线分为水平线和垂直线，然后折叠重叠的线
     * 2. Detect cells formed by the intersection of horizontal and vertical rulings
     * 2. 检测由水平和垂直标线交叉形成的单元格
     * 3. Group cells into spreadsheet areas
     * 3. 将单元格分组为电子表格区域
     * 4. For each area, collect overlapping cells and rulings with text content
     * 4. 对每个区域，收集重叠的单元格和包含文本内容的标线
     * 5. Construct Table objects with all associated data
     * 5. 构建包含所有相关数据的Table对象
     *
     * @param page The PDF page object containing text elements and positional information
     *             PDF页面对象，包含文本元素和位置信息
     * @param rulings List of ruling lines detected on the page that serve as cell boundaries
     *                在页面上检测到的标线列表，用作单元格边界
     * @return List of extracted Table objects sorted by their position on the page
     *         提取的Table对象列表，按在页面上的位置排序
     */
    public List<Table> extract(Page page, List<Ruling> rulings) {
        // Split rulings into horizontal and vertical categories
        // 将标线分为水平和垂直两类
        List<Ruling> horizontalR = new ArrayList<>();
        List<Ruling> verticalR = new ArrayList<>();
        
        for (Ruling r: rulings) {
            if (r.horizontal()) {
                horizontalR.add(r);
            }
            else if (r.vertical()) {
                verticalR.add(r);
            }
        }
        horizontalR = Ruling.collapseOrientedRulings(horizontalR);
        verticalR = Ruling.collapseOrientedRulings(verticalR);
        
        // Identify individual cells from the intersection of horizontal and vertical rulings
        // 从水平和垂直标线的交点识别单个单元格
        List<Cell> cells = findCells(horizontalR, verticalR);

        // Group cells into larger spreadsheet regions
        // 将单元格分组为更大的电子表格区域
        List<Rectangle> spreadsheetAreas = findSpreadsheetsFromCells(cells);

        List<Table> spreadsheets = new ArrayList<>();
        for (Rectangle area: spreadsheetAreas) {

            // Collect cells that fully belong to this area; containment keeps outer frames
            // (which contain the area the other way round) out of the table
            // 收集完全属于此区域的单元格；包含语义可挡住反向包含的外框
            Rectangle inflatedArea = inflateByAdjacencyGap(area);
            List<Cell> overlappingCells = new ArrayList<>();
            for (Cell c: cells) {
                if (inflatedArea.contains(c)) {
                    c.setTextElements(TextElement.mergeWords(page.getText(c)));
                    overlappingCells.add(c);
                }
            }

            // Skip isolated empty cells: a region with a single cell that has no text is noise
            // 跳过孤立的空单元格：只有一个单元格且无文本的区域属于噪声
            if (overlappingCells.size() == 1 && overlappingCells.get(0).getText().isEmpty()) {
                continue;
            }

            // Collect horizontal ruling lines that fall within this spreadsheet area
            // 收集落入此电子表格区域内的水平标线
            List<Ruling> horizontalOverlappingRulings = new ArrayList<>();
            for (Ruling hr: horizontalR) {
                if (area.intersectsLine(hr)) {
                    horizontalOverlappingRulings.add(hr);
                }
            }

            // Collect vertical ruling lines that fall within this spreadsheet area
            // 收集落入此电子表格区域内的垂直标线
            List<Ruling> verticalOverlappingRulings = new ArrayList<>();
            for (Ruling vr: verticalR) {
                if (area.intersectsLine(vr)) {
                    verticalOverlappingRulings.add(vr);
                }
            }
                        
            // Create a Table object with cells, rulings, and page context information
            // 创建包含单元格、标线和页面上下文信息的Table对象
            TableWithRulingLines t = new TableWithRulingLines( page, area, overlappingCells, horizontalOverlappingRulings, verticalOverlappingRulings, this, page.getPageNumber());
            spreadsheets.add(t);
        }

        // Sort all extracted tables by their position on the page
        // 按位置对所有提取的表格进行排序
        Utils.sort(spreadsheets, Rectangle.ILL_DEFINED_ORDER);
        return spreadsheets;
    }
    
    public boolean isTabular(Page page) {
        
        // if there's no text at all on the page, it's not a table 
        // (we won't be able to do anything with it though)
        if (page.getText().isEmpty()){
            return false; 
        }

        // get minimal region of page that contains every character (in effect,
        // removes white "margins")
        Page minimalRegion = page.getArea(Utils.bounds(page.getText()));
        
        List<? extends Table> tables = new SpreadsheetExtractionAlgorithm().extract(minimalRegion);
        if (tables.isEmpty()) {
            return false;
        }
        Table table = tables.get(0);
        int rowsDefinedByLines = table.getRowCount();
        int colsDefinedByLines = table.getColCount();
        
        tables = new BasicExtractionAlgorithm().extract(minimalRegion);
        if (tables.isEmpty()) {
            return false;
        }
        table = tables.get(0);
        int rowsDefinedWithoutLines = table.getRowCount();
        int colsDefinedWithoutLines = table.getColCount();
        
        float ratio = (((float) colsDefinedByLines / colsDefinedWithoutLines) +
                ((float) rowsDefinedByLines / rowsDefinedWithoutLines)) / 2.0f;
        
        return ratio > MAGIC_HEURISTIC_NUMBER && ratio < (1 / MAGIC_HEURISTIC_NUMBER);
    }
    
    /**
     * Find table cells by detecting intersections of horizontal and vertical ruling lines
     * 通过水平和垂直标线的交点来查找表格单元格
     *
     * This algorithm identifies cells through the following steps:
     * 该算法通过以下步骤识别单元格：
     * 1. Calculate all intersections between horizontal and vertical lines
     * 1. 计算所有水平线和垂直线的交点
     * 2. Sort intersection points by Y coordinate first
     * 2. 对交点按Y坐标优先排序
     * 3. Iterate through each intersection as a potential top-left vertex
     * 3. 遍历每个交点作为潜在的左上角顶点
     * 4. Find the bottom-right vertex that shares the same rulings with the top-left to form a complete rectangular cell
     * 4. 寻找与左上角共享相同标线的右下角顶点，形成完整的矩形单元格
     *
     * @param horizontalRulingLines List of horizontal ruling lines that define the top and bottom boundaries of cells
     *                              水平标线列表，用于定义单元格的上下边界
     * @param verticalRulingLines List of vertical ruling lines that define the left and right boundaries of cells
     *                            垂直标线列表，用于定义单元格的左右边界
     * @return List of identified cells, each defined by top-left and bottom-right coordinates
     *         识别出的单元格列表，每个单元格由左上角和右下角坐标定义
     */
    public static List<Cell> findCells(List<Ruling> horizontalRulingLines, List<Ruling> verticalRulingLines) {
        List<Cell> cellsFound = new ArrayList<>();
        Map<Point2D, Ruling[]> intersectionPoints = Ruling.findIntersections(horizontalRulingLines, verticalRulingLines);
        List<Point2D> intersectionPointsList = new ArrayList<>(intersectionPoints.keySet());
        intersectionPointsList.sort(Y_FIRST_POINT_COMPARATOR);
        
        // Iterate through all intersection points to find complete rectangular cells
        // 遍历所有交点，尝试找到完整的矩形单元格
        for (int i = 0; i < intersectionPointsList.size(); i++) {
            Point2D topLeft = intersectionPointsList.get(i);
            Ruling[] hv = intersectionPoints.get(topLeft);

            List<Point2D> xPoints = new ArrayList<>();
            List<Point2D> yPoints = new ArrayList<>();

            // Collect subsequent intersection points that are on the same vertical or horizontal line as topLeft
            // 收集与topLeft在同一垂直线或水平线上的后续交点
            for (Point2D p: intersectionPointsList.subList(i, intersectionPointsList.size())) {
                if (p.getX() == topLeft.getX() && p.getY() > topLeft.getY()) {
                    xPoints.add(p);
                }
                if (p.getY() == topLeft.getY() && p.getX() > topLeft.getX()) {
                    yPoints.add(p);
                }
            }

            outer:
            for (Point2D xPoint: xPoints) {

                // is there a vertical edge b/w topLeft and xPoint?
                if (!hv[1].equals(intersectionPoints.get(xPoint)[1])) {
                    continue;
                }
                for (Point2D yPoint: yPoints) {
                    // is there an horizontal edge b/w topLeft and yPoint ?
                    if (!hv[0].equals(intersectionPoints.get(yPoint)[0])) {
                        continue;
                    }
                    Point2D btmRight = new Point2D.Float((float) yPoint.getX(), (float) xPoint.getY());
                    // Verify that four vertices form a complete rectangular cell
                    // 验证四个顶点是否形成完整的矩形单元格
                    if (intersectionPoints.containsKey(btmRight)
                            && intersectionPoints.get(btmRight)[0].equals(intersectionPoints.get(xPoint)[0])
                            && intersectionPoints.get(btmRight)[1].equals(intersectionPoints.get(yPoint)[1])) {
                            cellsFound.add(new Cell(topLeft, btmRight));
                        break outer;
                    }
                }
            }
        }
        
        // TODO create cells for vertical ruling lines with aligned endpoints at the top/bottom of a grid 
        // that aren't connected with an horizontal ruler?
        // see: https://github.com/jazzido/tabula-extractor/issues/78#issuecomment-41481207
        
        return cellsFound;
    }
    
    /**
     * Identify and merge complete spreadsheet regions from a list of cells
     * 从单元格列表中识别并合并出完整的电子表格区域
     *
     * This algorithm finds spreadsheet regions through the following steps:
     * 该算法通过以下步骤找到表格区域：
     * 1. Extract all cell vertices and remove shared vertices (internal vertices)
     * 1. 提取所有单元格的顶点，移除共享顶点（内部顶点）
     * 2. Sort remaining vertices by X and Y coordinates separately to establish horizontal and vertical edge mappings
     * 2. 对剩余顶点分别按X和Y坐标排序，建立水平和垂直边映射
     * 3. Build polygon contours by tracing edge mappings
     * 3. 通过追踪边映射构建多边形轮廓
     * 4. Calculate bounding rectangles for each polygon to obtain spreadsheet regions
     * 4. 计算每个多边形的外接矩形，得到表格区域
     *
     * @param cells List of cells that may contain multiple adjacent cells
     *              单元格列表，可能包含多个相邻的单元格
     * @return List of identified spreadsheet rectangular regions, each representing a complete table;
     *         outer-frame skeleton components (components whose members fully contain cells of
     *         other components) are excluded
     *         识别出的电子表格矩形区域列表，每个矩形代表一个完整的表格；
     *         外框骨架分量（成员完整包含其他分量单元格的分量）会被剔除
     */
    public static List<Rectangle> findSpreadsheetsFromCells(List<? extends Rectangle> cells) {
        // via: http://stackoverflow.com/questions/13746284/merging-multiple-adjacent-rectangles-into-one-polygon
        List<Rectangle> rectangles = new ArrayList<>();
        // List with forward scan replaces Set for vertex dedup: it is governed by feq tolerance rather than exact equals/hashCode,
        // the ascending-index loop is JIT-friendly and measured fastest, and List avoids HashSet's nondeterministic order
        // 顶点去重用 List 正序遍历替代 Set：由 feq 容差控制而非精确 equals/hashCode，
        // 升序索引循环对 JIT 友好且实测最快，同时 List 规避了 HashSet 的非确定性遍历顺序
        List<Point2D> pointList = new ArrayList<>();
        Map<Point2D, Point2D> edgesH = new HashMap<>();
        Map<Point2D, Point2D> edgesV = new HashMap<>();
        int i = 0;
        
        // Deduplicate (same references only: Rectangle2D.Float does not override hashCode),
        // then sort so the lists below stay deterministic
        // 去重（仅同对象引用：Rectangle2D.Float 未重写 hashCode），排序保证下方列表顺序确定
        List<Rectangle> dedupedCells = new ArrayList<>(new HashSet<>(cells));
        Utils.sort(dedupedCells, Rectangle.ILL_DEFINED_ORDER);

        // Build a spatial index over the cells to look up neighbors in O(log n)
        // 为单元格构建空间索引，以便在对数时间内查找相邻单元格
        RectangleSpatialIndex<Rectangle> index = new RectangleSpatialIndex<>();
        for (Rectangle c : dedupedCells) {
            index.add(c);
        }

        // Partition into connected components and drop outer-frame skeleton components;
        // same-component containment does not trigger the rule, keeping subdivided big cells
        // 分解为连通分量并剔除外框骨架分量；同分量内的包含不触发，保留细分大单元格
        CellPartition partition = partitionCells(dedupedCells, index);
        boolean[] skeleton = skeletonComponents(partition);
        // Rebuild the lists in sorted order (required by the vertex dedup below)
        // 按排序序重建列表（下方顶点去重所需）
        List<Rectangle> isolatedCells = new ArrayList<>();
        List<Rectangle> connectedCells = new ArrayList<>();
        for (int cellIdx = 0; cellIdx < dedupedCells.size(); cellIdx++) {
            int ci = partition.componentOfCell[cellIdx];
            if (skeleton[ci]) {
                continue;
            }
            if (partition.componentSizes[ci] > 1) {
                connectedCells.add(dedupedCells.get(cellIdx));
            } else {
                isolatedCells.add(dedupedCells.get(cellIdx));
            }
        }

        // Collect all cell vertices, remove shared internal vertices, keep only boundary vertices
        // 收集所有单元格顶点，移除共享的内部顶点，只保留边界顶点
        for (Rectangle cell: connectedCells) {
            for(Point2D pt: cell.getPoints()) {
                Point2D existing = findPointByFeq(pointList, pt);
                if (existing != null) { // shared vertex, remove it
                    pointList.remove(existing);
                }
                else {
                    pointList.add(pt);
                }
            }
        }
        
        // X first sort
        List<Point2D> pointsSortX = new ArrayList<>(pointList);
        pointsSortX.sort(X_FIRST_POINT_COMPARATOR);
        // Y first sort
        List<Point2D> pointsSortY = new ArrayList<>(pointList);
        pointsSortY.sort(Y_FIRST_POINT_COMPARATOR);

        // The vertex count is fixed once dedup is done, hoist it out of the loops
        // 顶点去重完成后数量不再变化，将 size 提取到循环外
        int pointCount = pointList.size();

        // Build horizontal edge mapping: pair adjacent vertices on the same horizontal line
        // 构建水平边映射：将同一水平线上的相邻顶点配对
        while (i < pointCount) {
            float currY = (float) pointsSortY.get(i).getY();
            while (i < pointCount && Utils.feq(pointsSortY.get(i).getY(), currY)) {
                edgesH.put(pointsSortY.get(i), pointsSortY.get(i+1));
                edgesH.put(pointsSortY.get(i+1), pointsSortY.get(i));
                i += 2;
            }
        }
        
        i = 0;
        // Build vertical edge mapping: pair adjacent vertices on the same vertical line
        // 构建垂直边映射：将同一垂直线上的相邻顶点配对
        while (i < pointCount) {
            float currX = (float) pointsSortX.get(i).getX();
            while (i < pointCount && Utils.feq(pointsSortX.get(i).getX(), currX)) {
                edgesV.put(pointsSortX.get(i), pointsSortX.get(i+1));
                edgesV.put(pointsSortX.get(i+1), pointsSortX.get(i));
                i += 2;
            }
        }
        
        // Get all the polygons
        // Build closed polygon contours by alternating between horizontal and vertical edge tracing
        // 通过交替追踪水平和垂直边，构建封闭的多边形轮廓
        List<List<PolygonVertex>> polygons = new ArrayList<>();
        Point2D nextVertex;
        while (!edgesH.isEmpty()) {
            ArrayList<PolygonVertex> polygon = new ArrayList<>();
            Point2D first = edgesH.keySet().iterator().next();
            polygon.add(new PolygonVertex(first, Direction.HORIZONTAL));
            edgesH.remove(first);
            
            while (true) {
                PolygonVertex curr = polygon.get(polygon.size() - 1);
                PolygonVertex lastAddedVertex;
                if (curr.direction == Direction.HORIZONTAL) {
                    nextVertex = edgesV.get(curr.point);
                    edgesV.remove(curr.point);
                    lastAddedVertex = new PolygonVertex(nextVertex, Direction.VERTICAL);
                }
                else {
                    nextVertex = edgesH.get(curr.point);
                    edgesH.remove(curr.point);
                    lastAddedVertex = new PolygonVertex(nextVertex, Direction.HORIZONTAL);
                }
                polygon.add(lastAddedVertex);

                if (lastAddedVertex.equals(polygon.get(0))) {
                    // closed polygon
                    polygon.remove(polygon.size() - 1);
                    break;
                }
            }
            
            // Clean up used vertices
            // 清理已使用的顶点
            for (PolygonVertex vertex: polygon) {
                edgesH.remove(vertex.point);
                edgesV.remove(vertex.point);
            }
            polygons.add(polygon);
        }
        
        // calculate grid-aligned minimum area rectangles for each found polygon
        // Calculate bounding rectangle (minimum bounding box) for each polygon
        // 计算每个多边形的外接矩形（最小包围盒）
        for(List<PolygonVertex> poly: polygons) {
            float top = java.lang.Float.MAX_VALUE;
            float left = java.lang.Float.MAX_VALUE;
            float bottom = java.lang.Float.MIN_VALUE;
            float right = java.lang.Float.MIN_VALUE;
            for (PolygonVertex pt: poly) {
                top = (float) Math.min(top, pt.point.getY());
                left = (float) Math.min(left, pt.point.getX());
                bottom = (float) Math.max(bottom, pt.point.getY());
                right = (float) Math.max(right, pt.point.getX());
            }
            rectangles.add(new Rectangle(top, left, right - left, bottom - top));
        }
        
        // Isolated cells (single-member components that are not skeletons) become independent
        // regions; their emptiness is decided later in extract
        // 孤立单元格（非骨架的单成员分量）作为独立区域返回；其是否为空的判定留给 extract 阶段处理
        for (Rectangle c : isolatedCells) {
            // copy to a plain Rectangle so the returned list never holds Cell instances,
            // matching the return type of the polygon-area path
            // 复制为纯 Rectangle，使返回列表不包含 Cell 子类实例，与多边形区域路径的返回类型一致
            rectangles.add(new Rectangle(c.getTop(), c.getLeft(), (float) c.getWidth(), (float) c.getHeight()));
        }
        
        return rectangles;
    }

    // Two cells are adjacent if their edges meet (within tolerance) with an overlapping extent
    // 两个单元格的边在容差范围内相接且范围重叠即为相邻
    private static boolean isAdjacent(Rectangle a, Rectangle b) {
        // horizontally adjacent (either side): right edge meets left edge with vertical overlap
        // 水平相邻（左右任一方向）：右边与左边相接且垂直方向重叠
        if ((Utils.within(a.getRight(), b.getLeft(), ADJACENCY_GAP) || Utils.within(b.getRight(), a.getLeft(), ADJACENCY_GAP))
                && a.verticallyOverlaps(b)) {
            return true;
        }
        // vertically adjacent (either side): bottom edge meets top edge with horizontal overlap
        // 垂直相邻（上下任一方向）：下边与上边相接且水平方向重叠
        if ((Utils.within(a.getBottom(), b.getTop(), ADJACENCY_GAP) || Utils.within(b.getBottom(), a.getTop(), ADJACENCY_GAP))
                && a.horizontallyOverlaps(b)) {
            return true;
        }
        return false;
    }

    /**
     * Partition result: each cell's component id, component sizes, and the cells each cell
     * fully contains (feeds the skeleton rule). Member lists are not kept.
     * 分解结果：分量编号、分量大小、各单元格完整包含的单元格（供骨架判定）；不保留成员列表
     */
    private static final class CellPartition {
        final int[] componentOfCell;
        final int[] componentSizes;
        final List<List<Integer>> containedIndicesOfCell;

        CellPartition(int[] componentOfCell, int[] componentSizes,
                List<List<Integer>> containedIndicesOfCell) {
            this.componentOfCell = componentOfCell;
            this.componentSizes = componentSizes;
            this.containedIndicesOfCell = containedIndicesOfCell;
        }
    }

    // A copy of r grown by the adjacency tolerance on every side, so that edge-touching
    // neighbors fall inside the envelope
    // 将 r 向四周按相邻容差放大的副本，使边相接的邻居落入包络内
    private static Rectangle inflateByAdjacencyGap(Rectangle r) {
        return new Rectangle(
                (float) (r.getTop() - ADJACENCY_GAP), (float) (r.getLeft() - ADJACENCY_GAP),
                (float) (r.getWidth() + 2 * ADJACENCY_GAP), (float) (r.getHeight() + 2 * ADJACENCY_GAP));
    }

    /**
     * Partition cells into connected components (edge contact within tolerance) via a flood
     * fill over the spatial index; the single inflated-envelope query per cell feeds both
     * the adjacency and the containment sweeps.
     * 通过空间索引上的泛洪填充，将单元格按边相接关系（容差内）分解为连通分量；
     * 每个单元格的一次膨胀包络查询同时服务相邻判定与包含判定
     *
     * @param cells deduplicated, deterministically sorted cells
     *              去重且确定性排序后的单元格
     * @param index spatial index built over the same cells
     *              基于同一批单元格构建的空间索引
     */
    private static CellPartition partitionCells(List<Rectangle> cells, RectangleSpatialIndex<Rectangle> index) {
        int n = cells.size();
        // Identity semantics on purpose: Rectangle2D.equals is value-based but hashCode is
        // not overridden, so value-based hashing would violate the equals contract
        // 刻意用引用语义：Rectangle2D.equals 是值语义但未重写 hashCode，值语义哈希违反 equals 契约
        Map<Rectangle, Integer> indexOf = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            indexOf.put(cells.get(i), i);
        }

        int[] componentOfCell = new int[n];
        Arrays.fill(componentOfCell, -1);
        List<Integer> componentSizes = new ArrayList<>();
        List<List<Integer>> containedIndicesOfCell = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            containedIndicesOfCell.add(null);
        }

        // Seeding over the sorted cells keeps component ids deterministic
        // 以排序后的单元格为种子遍历，保证分量编号确定
        for (int seed = 0; seed < n; seed++) {
            if (componentOfCell[seed] != -1) {
                continue;
            }
            int componentId = componentSizes.size();
            componentSizes.add(0);
            Deque<Integer> pending = new ArrayDeque<>();
            componentOfCell[seed] = componentId;
            pending.push(seed);
            while (!pending.isEmpty()) {
                int cur = pending.pop();
                componentSizes.set(componentId, componentSizes.get(componentId) + 1);
                Rectangle c = cells.get(cur);
                Rectangle inflated = inflateByAdjacencyGap(c);
                for (Rectangle other : index.intersects(inflated)) {
                    if (other == c) {
                        continue;
                    }
                    int j = indexOf.get(other);
                    // Adjacency is only evaluated for undiscovered candidates: cells already in
                    // a component are provably non-adjacent to the current one
                    // 仅对未发现的候选做相邻判定：已有分量归属的单元格必不与当前分量相邻
                    if (componentOfCell[j] == -1 && isAdjacent(c, other)) {
                        componentOfCell[j] = componentId;
                        pending.push(j);
                    }
                    if (c.contains(other)) {
                        if (containedIndicesOfCell.get(cur) == null) {
                            containedIndicesOfCell.set(cur, new ArrayList<>());
                        }
                        containedIndicesOfCell.get(cur).add(j);
                    }
                }
            }
        }
        return new CellPartition(componentOfCell, toIntArray(componentSizes), containedIndicesOfCell);
    }

    private static int[] toIntArray(List<Integer> sizes) {
        int[] rv = new int[sizes.size()];
        for (int i = 0; i < rv.length; i++) {
            rv[i] = sizes.get(i);
        }
        return rv;
    }

    /**
     * Mark skeleton components: a component whose member fully contains a cell of ANOTHER
     * component (e.g. a page border wrapping the real table). Same-component containment
     * does not trigger this, keeping subdivided big cells; a lone border cell is just the
     * single-member case of the same rule.
     * 标记骨架分量：成员完整包含另一分量单元格的分量（如罩住真实表格的页面边框）。
     * 同分量内的包含不触发，保留细分大单元格；孤立外框只是同一规则的单成员特例
     */
    private static boolean[] skeletonComponents(CellPartition partition) {
        boolean[] skeleton = new boolean[partition.componentSizes.length];
        for (int i = 0; i < partition.componentOfCell.length; i++) {
            List<Integer> contained = partition.containedIndicesOfCell.get(i);
            if (contained == null) {
                continue;
            }
            for (int j : contained) {
                if (partition.componentOfCell[j] != partition.componentOfCell[i]) {
                    skeleton[partition.componentOfCell[i]] = true;
                    break;
                }
            }
        }
        return skeleton;
    }
    
    @Override
    public String toString() {
        return "lattice";
    }
    
    private enum Direction {
        HORIZONTAL,
        VERTICAL
    }
    
     static class PolygonVertex {
        Point2D point;
        Direction direction;
        
        public PolygonVertex(Point2D point, Direction direction) {
            this.direction = direction;
            this.point = point;
        }
        
        @Override
        public boolean equals(Object other) {
            if (this == other) 
                return true;
            if (!(other instanceof PolygonVertex))
                return false;
            return this.point.equals(((PolygonVertex) other).point);
        }
        
        @Override
        public int hashCode() {
            return this.point.hashCode();
        }
        
        @Override
        public String toString() {
            return String.format("%s[point=%s,direction=%s]", this.getClass().getName(), this.point.toString(), this.direction.toString());
        }
    }
}
