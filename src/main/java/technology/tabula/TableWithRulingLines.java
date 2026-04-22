package technology.tabula;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import technology.tabula.extractors.ExtractionAlgorithm;

@SuppressWarnings("serial")
public class TableWithRulingLines extends Table {
    private static final float EPSILON1 = Utils.EPSILON;
    private static final float EPSILON2 = EPSILON1 + EPSILON1;

    Page         page;
    List<Ruling> verticalRulings, horizontalRulings;
    RectangleSpatialIndex<Cell> si = new RectangleSpatialIndex<>();

    public TableWithRulingLines(Page page, Rectangle area, List<Cell> cells, List<Ruling> horizontalRulings,
                                List<Ruling> verticalRulings, ExtractionAlgorithm extractionAlgorithm, int pageNumber) {
        super(extractionAlgorithm);
        this.page = page;
        this.setRect(area);
        this.verticalRulings = verticalRulings;
        this.horizontalRulings = horizontalRulings;
        this.addCells(cells);
        this.setPageNumber(pageNumber);
    }

    /**
     * Add cells to the table and organize them into rows and columns based on their spatial positions
     * 将单元格添加到表格中，并根据其空间位置将它们组织成行和列
     *
     * This method performs the following steps:
     * 该方法执行以下步骤：
     * 1. Add all cells to a spatial index for efficient querying
     * 1. 将所有单元格添加到空间索引中以进行高效查询
     * 2. Group cells into rows based on their top coordinate
     * 2. 根据单元格的顶部坐标将单元格分组为行
     * 3. For each row, calculate the starting column index by analyzing cells in the area below
     * 3. 对每一行，通过分析下方区域中的单元格来计算起始列索引
     * 4. Add cells to the table with their calculated row and column positions
     * 4. 使用计算出的行和列位置将单元格添加到表格中
     *
     * @param cells List of Cell objects to be added to the table
     *              要添加到表格的单元格对象列表
     */
    private void addCells(List<Cell> cells) {

        if (cells.isEmpty()) {
            return;
        }

        // Add all cells to the spatial index for efficient spatial queries
        // 将所有单元格添加到空间索引中以进行高效的空间查询
        for (Cell ce : cells) {
            si.add(ce);
        }

        // Group cells into rows based on their vertical position
        // 根据单元格的垂直位置将它们分组为行
        List<List<Cell>> rowsOfCells = rowsOfCells(cells);
        Rectangle bounds = si.getBounds();
        float boundsLeft = bounds.getLeft();
        float boundsBottom = bounds.getBottom();

        for (int i = 0; i < rowsOfCells.size(); i++) {
            List<Cell> row = rowsOfCells.get(i);
            Iterator<Cell> rowCells = row.iterator();
            Cell cell = rowCells.next();

            // Find cells in the area below the current cell to determine column offset
            // 在当前单元格下方的区域中查找单元格以确定列偏移量
            List<List<Cell>> others = rowsOfCells(si.contains(
                    new Rectangle(cell.getBottom(), boundsLeft, cell.getLeft() - boundsLeft,
                            boundsBottom - cell.getBottom())));

            // Calculate the starting column index based on the maximum number of cells in rows below
            // 基于下方各行中的最大单元格数计算起始列索引
            int startColumn = 0;
            for (List<Cell> r : others) {
                startColumn = Math.max(startColumn, r.size());
            }

            // Detect and extract text content in the left margin area before the first cell
            // 检测并提取第一个单元格左侧空白区域中的文本内容
            if (startColumn > 0) {
                // Create a temporary cell representing the left margin area with epsilon adjustments to avoid overlaps
                // 创建一个表示左侧空白区域的临时单元格，使用epsilon调整以避免重叠
                Cell leftCell = new Cell(cell.getTop() + EPSILON1, boundsLeft, cell.getLeft() - boundsLeft - EPSILON1,
                        (float) cell.getHeight() - EPSILON2);

                // Check if there are no existing cells intersecting with the left margin area
                // 检查左侧空白区域是否与现有单元格无交集
                if (leftCell.getWidth() > 0 && leftCell.getHeight() > 0 && si.intersects(leftCell).isEmpty()) {
                    // Adjust the cell boundaries to the actual position
                    // 将单元格边界调整为实际位置
                    leftCell.setTop(cell.getTop());
                    leftCell.setRight(cell.getLeft());
                    leftCell.setBottom(cell.getBottom());

                    // Extract text elements from the page within the left margin area
                    // 从页面中提取左侧空白区域内的文本元素
                    List<TextElement> pageText = page.getText(leftCell);
                    if (!pageText.isEmpty()) {
                        leftCell.setTextElements(TextElement.mergeWords(pageText));
                        // Add the left margin cell to the table at column 0 if it contains non-empty text
                        // 如果左侧单元格包含非空文本，则将其添加到表格的第0列
                        if (!leftCell.getText(false).isEmpty()) {
                            this.add(leftCell, i, 0);
                        }
                    }
                }
            }

            this.add(cell, i, startColumn++);
            while (rowCells.hasNext()) {
                this.add(rowCells.next(), i, startColumn++);
            }
        }
    }

    /**
     * Group cells into rows based on their top coordinate (y-position)
     * 根据单元格的顶部坐标（y位置）将单元格分组为行
     *
     * This method sorts cells by their top coordinate and groups cells that share
     * the same top position into the same row. Floating point comparison uses
     * epsilon equality to handle minor precision differences.
     * 该方法按顶部坐标对单元格进行排序，并将具有相同顶部位置的单元格分组到同一行中。
     * 浮点数比较使用epsilon相等性来处理微小的精度差异。
     *
     * @param cells List of cells to be grouped into rows
     *              要分组为行的单元格列表
     * @return A list of rows, where each row is a list of cells at the same vertical position
     *         行的列表，其中每行是在相同垂直位置的单元格列表
     */
    private static List<List<Cell>> rowsOfCells(List<Cell> cells) {
        Cell c;
        float lastTop;
        List<List<Cell>> rv = new ArrayList<>();
        List<Cell> lastRow;

        if (cells.isEmpty()) {
            return rv;
        }

        // Sort cells by top coordinate first, then by left coordinate in ascending order
        // 首先按顶部坐标升序对单元格进行排序，其次按左右坐标升序
        cells.sort((cell0, cell1) -> {
            double top0 = cell0.getTop();
            double top1 = cell1.getTop();

            if (Utils.feq(top0, top1)) {
                double left0 = cell0.getLeft();
                double left1 = cell1.getLeft();

                if (Utils.feq(left0, left1)) {
                    return 0;
                }
                return java.lang.Double.compare(left0, left1);
            }
            return java.lang.Double.compare(top0, top1);
        });

        Iterator<Cell> iter = cells.iterator();
        c = iter.next();
        lastTop = c.getTop();
        lastRow = new ArrayList<>();
        lastRow.add(c);
        rv.add(lastRow);

        // Iterate through sorted cells and group them by top coordinate
        // 遍历排序后的单元格并按顶部坐标进行分组
        while (iter.hasNext()) {
            c = iter.next();
            // Start a new row when the top coordinate changes significantly
            // 当顶部坐标发生显著变化时开始新行
            if (!Utils.feq(c.getTop(), lastTop)) {
                lastRow = new ArrayList<>();
                rv.add(lastRow);
            }
            lastRow.add(c);
            lastTop = c.getTop();
        }
        return rv;
    }

}
