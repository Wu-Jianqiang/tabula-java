tabula-java [![Build Status](https://travis-ci.org/tabulapdf/tabula-java.svg?branch=master)](https://travis-ci.org/tabulapdf/tabula-java)
===========

## Branch Description

This is a maintenance branch of `tabula-java`. Since the original project has not been updated for a long time, and we encountered some bugs that must be fixed in practical work, we created this branch and made it publicly available. The main updates are as follows:

1. The branch project continues to maintain JDK 8 compatibility, and the latest version can be obtained from [Maven Central Repository](https://central.sonatype.com/artifact/io.github.wu-jianqiang/tabula/versions);
2. Fixed two logical bugs in table line segment generation, solving the problem of residual segments and alignment;
3. Enhanced debug image generation functionality with zoom scale, defaulting to 4x magnification, significantly improving the clarity and accuracy of visualized debug output.

**Important Statement:**
- This branch is developed based on the original `tabula-java` project and follows the same MIT license
- Thanks to the original author Manuel Aristarán for his contribution
- If you have feature enhancements or bug fixes, feel free to submit PRs, we will consider merging them into the main branch
- When using this branch, please pay attention to the differences with the original project and choose the appropriate version according to your needs

## 分支说明

此为 `tabula-java` 的一个维护性分支。由于原项目长期未更新，而在实际工作中遇到了必须修复的 bug，因此创建了这个分支并将其公开分享。主要更新点如下：

1. 分支项目继续保留 JDK 8 版本兼容性，可在 [Maven Central Repository](https://central.sonatype.com/artifact/io.github.wu-jianqiang/tabula/versions) 获取最新版本；
2. 修复了两处表格线段生成的逻辑 bug，解决了残留线段和对齐问题；
3. 调试图像生成功能增加缩放比例，默认放大 4 倍，显著提高了可视化调试输出的清晰度和准确性。

**重要声明：**
- 此分支基于原始 `tabula-java` 项目进行开发，遵循相同的 MIT 许可证
- 感谢原作者 Manuel Aristarán 的贡献
- 如有功能增强或 bug 修复，欢迎提交 PR，我们会考虑合并到主分支
- 使用此分支时，请注意与原始项目的差异，并根据需要选择合适的版本

===========

`tabula-java` is a library for extracting tables from PDF files — it is the table extraction engine that powers [Tabula](http://tabula.technology/) ([repo](http://github.com/tabulapdf/tabula)). You can use `tabula-java` as a command-line tool to programmatically extract tables from PDFs.

© 2014-2020 Manuel Aristarán. Available under MIT License. See [`LICENSE`](LICENSE).

## Download

Download a version of the tabula-java's jar, with all dependencies included, that works on Mac, Windows and Linux from our [releases page](../../releases).

## Commandline Usage Examples

`tabula-java` provides a command line application:

```
$ java -jar target/tabula-1.0.5-jar-with-dependencies.jar --help
usage: tabula [-a <AREA>] [-b <DIRECTORY>] [-c <COLUMNS>] [-f <FORMAT>]
       [-g] [-h] [-i] [-l] [-n] [-o <OUTFILE>] [-p <PAGES>] [-r] [-s
       <PASSWORD>] [-t] [-u] [-v]

Tabula helps you extract tables from PDFs

 -a,--area <AREA>           -a/--area = Portion of the page to analyze.
                            Example: --area 269.875,12.75,790.5,561.
                            Accepts top,left,bottom,right i.e. y1,x1,y2,x2
                            where all values are in points relative to the
                            top left corner. If all values are between
                            0-100 (inclusive) and preceded by '%', input
                            will be taken as % of actual height or width
                            of the page. Example: --area %0,0,100,50. To
                            specify multiple areas, -a option should be
                            repeated. Default is entire page
 -b,--batch <DIRECTORY>     Convert all .pdfs in the provided directory.
 -c,--columns <COLUMNS>     X coordinates of column boundaries. Example
                            --columns 10.1,20.2,30.3. If all values are
                            between 0-100 (inclusive) and preceded by '%',
                            input will be taken as % of actual width of
                            the page. Example: --columns %25,50,80.6
 -f,--format <FORMAT>       Output format: (CSV,TSV,JSON). Default: CSV
 -g,--guess                 Guess the portion of the page to analyze per
                            page.
 -h,--help                  Print this help text.
 -i,--silent                Suppress all stderr output.
 -l,--lattice               Force PDF to be extracted using lattice-mode
                            extraction (if there are ruling lines
                            separating each cell, as in a PDF of an Excel
                            spreadsheet)
 -n,--no-spreadsheet        [Deprecated in favor of -t/--stream] Force PDF
                            not to be extracted using spreadsheet-style
                            extraction (if there are no ruling lines
                            separating each cell)
 -o,--outfile <OUTFILE>     Write output to <file> instead of STDOUT.
                            Default: -
 -p,--pages <PAGES>         Comma separated list of ranges, or all.
                            Examples: --pages 1-3,5-7, --pages 3 or
                            --pages all. Default is --pages 1
 -r,--spreadsheet           [Deprecated in favor of -l/--lattice] Force
                            PDF to be extracted using spreadsheet-style
                            extraction (if there are ruling lines
                            separating each cell, as in a PDF of an Excel
                            spreadsheet)
 -s,--password <PASSWORD>   Password to decrypt document. Default is empty
 -t,--stream                Force PDF to be extracted using stream-mode
                            extraction (if there are no ruling lines
                            separating each cell)
 -u,--use-line-returns      Use embedded line returns in cells. (Only in
                            spreadsheet mode.)
 -v,--version               Print version and exit.
```

It also includes a debugging tool, run `java -cp ./target/tabula-1.0.5-jar-with-dependencies.jar technology.tabula.debug.Debug -h` for the available options.

You can also integrate `tabula-java` with any JVM language. For Java examples, see the [`tests`](src/test/java/technology/tabula/) folder.

JVM start-up time is a lot of the cost of the `tabula` command, so if you're trying to extract many tables from PDFs, you have a few options for speeding it up:

 - the -b option, which allows you to convert all pdfs in a given directory
 - the [drip](https://github.com/ninjudd/drip) utility
 - the [Ruby](http://github.com/tabulapdf/tabula-extractor), [Python](https://github.com/chezou/tabula-py), [R](https://github.com/leeper/tabulizer), and [Node.js](https://github.com/ezodude/tabula-js) bindings
 - writing your own program in any JVM language (Java, JRuby, Scala) that imports tabula-java.
 - waiting for us to implement an API/server-style system (it's on the [roadmap](https://github.com/tabulapdf/tabula-api))

## API Usage Examples

A simple Java code example which extracts all rows and cells from all tables of all pages of a PDF document:

```java
InputStream in = this.getClass().getResourceAsStream("my.pdf");
try (PDDocument document = PDDocument.load(in)) {
    SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
    PageIterator pi = new ObjectExtractor(document).extract();
    while (pi.hasNext()) {
        // iterate over the pages of the document
        Page page = pi.next();
        List<Table> table = sea.extract(page);
        // iterate over the tables of the page
        for(Table tables: table) {
            List<List<RectangularTextContainer>> rows = tables.getRows();
            // iterate over the rows of the table
            for (List<RectangularTextContainer> cells : rows) {
                // print all column-cells of the row plus linefeed
                for (RectangularTextContainer content : cells) {
                    // Note: Cell.getText() uses \r to concat text chunks
                    String text = content.getText().replace("\r", " ");
                    System.out.print(text + "|");
                }
                System.out.println();
            }
        }
    }
}
```


For more detail information check the Javadoc. 
The Javadoc API documentation can be generated (see also '_Building from Source_' section) via

```
mvn javadoc:javadoc
```

which generates the HTML files to directory ```target/site/apidocs/```

## Building from Source

Clone this repo and run:

```
mvn clean compile assembly:single
```

## Contributing

Interested in helping out? We'd love to have your help!

You can help by:

- [Reporting a bug](https://github.com/tabulapdf/tabula-java/issues).
- Adding or editing documentation.
- Contributing code via a Pull Request.
- Spreading the word about `tabula-java` to people who might be able to benefit from using it.

### Backers

You can also support our continued work on `tabula-java` with a one-time or monthly donation [on OpenCollective](https://opencollective.com/tabulapdf#support). Organizations who use `tabula-java` can also [sponsor the project](https://opencollective.com/tabulapdf#support) for acknowledgement on [our official site](http://tabula.technology/) and this README.

Special thanks to the following users and organizations for generously supporting Tabula with donations and grants:

<a href="https://opencollective.com/tabulapdf/backer/0/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/0/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/1/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/1/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/2/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/2/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/3/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/3/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/4/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/4/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/5/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/5/avatar"></a>

<a title="The John S. and James L. Knight Foundation" href="http://www.knightfoundation.org/" target="_blank"><img alt="The John S. and James L. Knight Foundation" src="https://knightfoundation.org/wp-content/uploads/2019/10/KF_Logotype_Icon-and-Stacked-Name.png" width="300"></a>
<a title="The Shuttleworth Foundation" href="https://shuttleworthfoundation.org/" target="_blank"><img width="200" alt="The Shuttleworth Foundation" src="https://raw.githubusercontent.com/tabulapdf/tabula/gh-pages/shuttleworth.jpg"></a>
