#!/usr/bin/env python3
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "mall-order-milvus-rag/src/main/resources/data/08_销售业务手册.pdf"
FONT_PATH = Path("/System/Library/Fonts/Supplemental/Arial Unicode.ttf")


def register_fonts():
    if not FONT_PATH.exists():
        raise SystemExit(f"Chinese font not found: {FONT_PATH}")
    pdfmetrics.registerFont(TTFont("SalesCN", str(FONT_PATH)))


def page_decor(canvas, doc):
    canvas.saveState()
    canvas.setFont("SalesCN", 8)
    canvas.setFillColor(colors.HexColor("#667085"))
    canvas.drawString(20 * mm, 12 * mm, "商城订单 Agent 演示知识库 · 销售业务手册 v1.0")
    canvas.drawRightString(190 * mm, 12 * mm, f"第 {doc.page} 页")
    canvas.setStrokeColor(colors.HexColor("#D0D5DD"))
    canvas.line(20 * mm, 17 * mm, 190 * mm, 17 * mm)
    canvas.restoreState()


def build_pdf():
    register_fonts()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        leftMargin=20 * mm,
        rightMargin=20 * mm,
        topMargin=18 * mm,
        bottomMargin=24 * mm,
        title="销售业务手册",
        author="商城订单 Agent 演示项目",
    )
    base = getSampleStyleSheet()
    styles = {
        "title": ParagraphStyle(
            "title", parent=base["Title"], fontName="SalesCN", fontSize=25,
            leading=33, textColor=colors.HexColor("#173F35"), alignment=TA_CENTER,
            spaceAfter=8 * mm,
        ),
        "subtitle": ParagraphStyle(
            "subtitle", parent=base["Normal"], fontName="SalesCN", fontSize=11,
            leading=18, textColor=colors.HexColor("#475467"), alignment=TA_CENTER,
        ),
        "h1": ParagraphStyle(
            "h1", parent=base["Heading1"], fontName="SalesCN", fontSize=18,
            leading=25, textColor=colors.HexColor("#173F35"), spaceBefore=2 * mm,
            spaceAfter=5 * mm,
        ),
        "h2": ParagraphStyle(
            "h2", parent=base["Heading2"], fontName="SalesCN", fontSize=13,
            leading=19, textColor=colors.HexColor("#8A5A00"), spaceBefore=4 * mm,
            spaceAfter=2 * mm,
        ),
        "body": ParagraphStyle(
            "body", parent=base["BodyText"], fontName="SalesCN", fontSize=10,
            leading=17, textColor=colors.HexColor("#344054"), spaceAfter=2 * mm,
        ),
        "table_head": ParagraphStyle(
            "table_head", parent=base["BodyText"], fontName="SalesCN", fontSize=10,
            leading=17, textColor=colors.white,
        ),
        "note": ParagraphStyle(
            "note", parent=base["BodyText"], fontName="SalesCN", fontSize=9.5,
            leading=16, textColor=colors.HexColor("#173F35"), backColor=colors.HexColor("#ECF7F2"),
            borderPadding=8, borderColor=colors.HexColor("#A6D5C4"), borderWidth=0.5,
            spaceBefore=3 * mm, spaceAfter=3 * mm,
        ),
    }

    story = [
        Spacer(1, 24 * mm),
        Paragraph("销售业务手册", styles["title"]),
        Paragraph("客户跟进 · 订单协同 · 报价边界 · 售后协作", styles["subtitle"]),
        Spacer(1, 18 * mm),
        Paragraph("适用范围", styles["h1"]),
        Paragraph(
            "本手册用于本地演示环境中的销售角色知识问答。销售人员可以查询自己被分配客户的订单，"
            "但不能代替客户执行取消、退款、退货、换货或修改地址等操作。", styles["body"]),
        Paragraph(
            "重要边界：销售视图中的联系电话与收货地址必须脱敏；客户本人确认后，敏感订单操作才可进入售后流程。",
            styles["note"]),
        Paragraph("角色职责", styles["h2"]),
        Paragraph("1. 了解客户需求与订单进展，记录明确的跟进结论。", styles["body"]),
        Paragraph("2. 对异常订单发起内部协同，不对客户承诺未获批准的价格、时效或补偿。", styles["body"]),
        Paragraph("3. 引导客户通过本人身份完成敏感操作确认，销售不得代操作。", styles["body"]),
        Paragraph("4. 仅查看系统分配给自己的客户，不传播客户隐私和订单明细。", styles["body"]),
        PageBreak(),
        Paragraph("一、客户与订单跟进", styles["h1"]),
        Paragraph("销售跟进应围绕订单事实、客户诉求和下一步责任人展开。查询订单时先确认客户是否在自己的分配范围内。", styles["body"]),
    ]

    data = [
        [Paragraph("场景", styles["table_head"]), Paragraph("可执行动作", styles["table_head"]), Paragraph("禁止事项", styles["table_head"])],
        [Paragraph("订单已付款", styles["body"]), Paragraph("说明当前状态，协同仓储确认预计出库时间", styles["body"]), Paragraph("承诺未经确认的发货日期", styles["body"])],
        [Paragraph("订单已发货", styles["body"]), Paragraph("同步物流状态，记录异常并转物流协同", styles["body"]), Paragraph("擅自修改已发货订单地址", styles["body"])],
        [Paragraph("客户要求退款", styles["body"]), Paragraph("说明规则，引导客户本人发起并确认售后申请", styles["body"]), Paragraph("代客户提交或确认退款", styles["body"])],
        [Paragraph("订单已完成", styles["body"]), Paragraph("核对售后时限和商品状态，转客服处理", styles["body"]), Paragraph("直接承诺退款结果", styles["body"])],
    ]
    table = Table(data, colWidths=[34 * mm, 72 * mm, 58 * mm], repeatRows=1)
    table.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), "SalesCN"),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#173F35")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#D0D5DD")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F9FAFB")]),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    story.extend([
        table,
        Spacer(1, 5 * mm),
        Paragraph("跟进记录最小要素", styles["h2"]),
        Paragraph("每次跟进至少记录：客户、订单号、当前状态、客户原始诉求、已说明规则、下一责任人和预计回访时间。", styles["body"]),
        Paragraph("订单数据以系统实时结果为准。销售页面显示的电话和地址为脱敏信息，不应要求绕过脱敏查看。", styles["note"]),
        Paragraph("二、报价与承诺边界", styles["h1"]),
        Paragraph("标准商品按已发布价格和活动规则报价。非标准折扣、额外赠品、运费减免和补偿必须经过审批。", styles["body"]),
        Paragraph("不得使用“保证当天到货”“一定退款成功”“可以跳过审核”等绝对承诺。推荐表述为：已记录诉求，将按订单规则转交对应团队确认。", styles["body"]),
        PageBreak(),
        Paragraph("三、退款、退货与售后协作", styles["h1"]),
        Paragraph("销售负责解释、记录和协同，客户本人负责发起并确认敏感操作，客服或售后团队负责审核与处理。", styles["body"]),
        Paragraph("标准协作流程", styles["h2"]),
        Paragraph("1. 核对客户归属和订单状态，仅查看已分配客户订单。", styles["body"]),
        Paragraph("2. 复述客户诉求，区分退款、退货、换货、取消订单或修改地址。", styles["body"]),
        Paragraph("3. 引用订单与售后规则，说明可能的限制和所需材料。", styles["body"]),
        Paragraph("4. 引导客户切换到本人演示身份，完成明确确认。", styles["body"]),
        Paragraph("5. 生成售后单后记录工单号，并按约定时间回访。", styles["body"]),
        Paragraph("特殊场景", styles["h2"]),
        Paragraph("已发货订单通常不能直接修改地址，应先联系物流或客服评估；待付款或已取消订单不进入普通退款售后流程；已完成订单需结合售后时限与商品状态判断。", styles["body"]),
        Paragraph("隐私与数据使用", styles["h2"]),
        Paragraph("销售只可将订单信息用于当前客户服务。不得复制完整电话、地址，不得查询未分配客户，不得把演示身份描述成正式账号权限。", styles["body"]),
        Paragraph("演示提示", styles["note"]),
        Paragraph("在页面中选择王磊时仅能看到 USER1001 的订单；选择刘婷时仅能看到 USER1002 的订单。切换到客户身份后，才可以演示取消或售后确认。", styles["body"]),
    ])

    doc.build(story, onFirstPage=page_decor, onLaterPages=page_decor)
    print(OUTPUT)


if __name__ == "__main__":
    build_pdf()
