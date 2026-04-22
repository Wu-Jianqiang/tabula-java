package technology.tabula.extractors;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import technology.tabula.Cell;
import technology.tabula.Page;
import technology.tabula.Rectangle;
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

            // Collect all cells that intersect with this spreadsheet area and extract their text content
            // 收集与此电子表格区域相交的所有单元格并提取其文本内容
            List<Cell> overlappingCells = new ArrayList<>();
            for (Cell c: cells) {
                if (c.intersects(area)) {
                    c.setTextElements(TextElement.mergeWords(page.getText(c)));
                    overlappingCells.add(c);
                }
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
     * @return List of identified spreadsheet rectangular regions, each representing a complete table
     *         识别出的电子表格矩形区域列表，每个矩形代表一个完整的表格
     */
    public static List<Rectangle> findSpreadsheetsFromCells(List<? extends Rectangle> cells) {
        // via: http://stackoverflow.com/questions/13746284/merging-multiple-adjacent-rectangles-into-one-polygon
        List<Rectangle> rectangles = new ArrayList<>();
        Set<Point2D> pointSet = new HashSet<>();
        Map<Point2D, Point2D> edgesH = new HashMap<>();
        Map<Point2D, Point2D> edgesV = new HashMap<>();
        int i = 0;
        
        // Deduplicate and sort cells
        // 去重并排序单元格
        cells = new ArrayList<>(new HashSet<>(cells));

        Utils.sort(cells, Rectangle.ILL_DEFINED_ORDER);

        // Collect all cell vertices, remove shared internal vertices, keep only boundary vertices
        // 收集所有单元格顶点，移除共享的内部顶点，只保留边界顶点
        for (Rectangle cell: cells) {
            for(Point2D pt: cell.getPoints()) {
                if (pointSet.contains(pt)) { // shared vertex, remove it
                    pointSet.remove(pt);
                }
                else {
                    pointSet.add(pt);
                }
            }
        }
        
        // X first sort
        List<Point2D> pointsSortX = new ArrayList<>(pointSet);
        pointsSortX.sort(X_FIRST_POINT_COMPARATOR);
        // Y first sort
        List<Point2D> pointsSortY = new ArrayList<>(pointSet);
        pointsSortY.sort(Y_FIRST_POINT_COMPARATOR);
        
        // Build horizontal edge mapping: pair adjacent vertices on the same horizontal line
        // 构建水平边映射：将同一水平线上的相邻顶点配对
        while (i < pointSet.size()) {
            float currY = (float) pointsSortY.get(i).getY();
            while (i < pointSet.size() && Utils.feq(pointsSortY.get(i).getY(), currY)) {
                edgesH.put(pointsSortY.get(i), pointsSortY.get(i+1));
                edgesH.put(pointsSortY.get(i+1), pointsSortY.get(i));
                i += 2;
            }
        }
        
        i = 0;
        // Build vertical edge mapping: pair adjacent vertices on the same vertical line
        // 构建垂直边映射：将同一垂直线上的相邻顶点配对
        while (i < pointSet.size()) {
            float currX = (float) pointsSortX.get(i).getX();
            while (i < pointSet.size() && Utils.feq(pointsSortX.get(i).getX(), currX)) {
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
        
        return rectangles;
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
