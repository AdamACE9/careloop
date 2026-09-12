package com.careloop.app.ui.screens.vitals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.CheckIn
import com.careloop.app.data.model.CheckInStatus
import com.careloop.app.data.model.VitalReading
import com.careloop.app.data.model.VitalType
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CareEmptyState
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.components.SectionHeader
import com.careloop.app.ui.components.StatusPill
import com.careloop.app.ui.components.initialSeed
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Margaret's vitals screen.
 *
 * This is deliberately **not a clinical dashboard**. It belongs to Margaret, not to a
 * clinician and not to Sarah — so every number is followed by a plain-language read of what
 * it means, in Cara's voice, and nothing here is styled to feel like monitoring or
 * surveillance. Calm colours, generous whitespace, no red unless something genuinely needs
 * attention (and even then, always paired with words and an icon — never colour alone).
 *
 * Layout, top to bottom:
 * 1. Today's blood sugar reading, large, with a plain-language in/out-of-range statement.
 * 2. A 14-day blood sugar trend, hand-drawn with Compose [Canvas] (no charting library is
 *    available), plus Cara's own read of the trend — derived from the data, not hardcoded.
 * 3. Recent blood pressure readings as a simple list.
 * 4. A manual "Record a reading" fallback for readings taken between calls.
 */
@Composable
fun VitalsScreen(
    onRecordReading: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Firestore-shaped flow, collected lifecycle-safely. The initial value is what renders
    // on the very first frame, before the flow has had a chance to emit — initialSeed picks
    // MockData only in demo mode; a real signed-in account seeds empty (see its doc).
    val bloodSugarReadings by AppContainer.repository
        .observeVitals(VitalType.BLOOD_SUGAR)
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.bloodSugarReadings),
        )

    // This used to be `MockData.bloodPressureReadings` unconditionally, regardless of what
    // repository was actually configured — a real signed-in account would have seen
    // Margaret's blood pressure forever, since nothing here ever asked the repository for
    // their own. It's a live query now, exactly like blood sugar above it.
    val bloodPressureReadings by AppContainer.repository
        .observeVitals(VitalType.BLOOD_PRESSURE)
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.bloodPressureReadings),
        )

    // Check-ins double as the adherence record here — each one already carries a day's
    // COMPLETED/MISSED_DOSE/NO_ANSWER/ESCALATED status, so the dot-per-day strip below reads
    // it directly rather than the screen inventing a second, parallel adherence field.
    val checkIns by AppContainer.repository.observeCheckIns()
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.checkIns),
        )

    VitalsScreenContent(
        bloodSugarReadings = bloodSugarReadings,
        bloodPressureReadings = bloodPressureReadings,
        checkIns = checkIns,
        onRecordReading = onRecordReading,
        modifier = modifier,
    )
}

/**
 * Stateless content, split out from [VitalsScreen] so it can be previewed and reasoned about
 * without needing a live repository or a [androidx.lifecycle.LifecycleOwner].
 */
@Composable
private fun VitalsScreenContent(
    bloodSugarReadings: List<VitalReading>,
    bloodPressureReadings: List<VitalReading>,
    checkIns: List<CheckIn>,
    onRecordReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedBloodSugar = bloodSugarReadings.sortedBy { it.recordedAt }
    val latestBloodSugar = sortedBloodSugar.lastOrNull()
    val hasAnyVitals = bloodSugarReadings.isNotEmpty() || bloodPressureReadings.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CareDimens.ScreenPadding, vertical = CareDimens.SpaceLg),
    ) {
        Text(
            text = "Your readings",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            text = "From your check-ins with Cara.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        if (latestBloodSugar != null) {
            TodayReadingCard(reading = latestBloodSugar)
            Spacer(Modifier.height(CareDimens.SpaceXl))
        } else if (!hasAnyVitals) {
            // One shared empty state for the whole screen rather than three separate
            // near-empty widgets (today's reading, the trend chart, the blood pressure
            // list) each saying their own small version of "nothing here yet" — CLAUDE.md's
            // TASK 1 asks for one clear sentence and one obvious action, not three quiet ones.
            CareEmptyState(
                title = "No readings yet",
                whatHappensNext = "Record a reading below, or Cara will ask for one on " +
                    "your next call.",
            )
            Spacer(Modifier.height(CareDimens.SpaceXl))
        }

        SectionHeader(title = "Your blood sugar, these last two weeks")
        CareCard {
            BloodSugarTrendChart(
                readings = sortedBloodSugar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )

            Spacer(Modifier.height(CareDimens.SpaceMd))
            ChartLegend()

            if (sortedBloodSugar.size >= 3) {
                Spacer(Modifier.height(CareDimens.SpaceLg))
                CaraTrendNote(text = describeBloodSugarTrend(sortedBloodSugar))
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        SectionHeader(
            title = "Taking your medications, day by day",
            subtitle = "Your last week, at a glance.",
        )
        CareCard {
            AdherenceStrip(checkIns = checkIns)
            Spacer(Modifier.height(CareDimens.SpaceLg))
            AdherenceLegend()
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        SectionHeader(title = "Your blood pressure")
        CareCard {
            if (bloodPressureReadings.isEmpty()) {
                Text(
                    text = "No blood pressure readings recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                bloodPressureReadings
                    .sortedByDescending { it.recordedAt }
                    .forEachIndexed { index, reading ->
                        BloodPressureRow(reading = reading)
                        if (index != bloodPressureReadings.lastIndex) {
                            Spacer(Modifier.height(CareDimens.SpaceMd))
                        }
                    }
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        CarePrimaryButton(
            text = "Record a reading",
            onClick = onRecordReading,
            icon = Icons.Rounded.Add,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))
    }
}

// ---------------------------------------------------------------------------
// Today's reading
// ---------------------------------------------------------------------------

private val recordedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayReadingCard(
    reading: VitalReading,
    modifier: Modifier = Modifier,
) {
    val status = bloodSugarStatus(reading)

    CareCard(modifier) {
        Text(
            text = "Today's blood sugar",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceSm))

        // FlowRow, not Row: the big display-scale number and its unit sit comfortably on
        // one line at normal text size, but displayMedium at 200% scale can push the unit
        // past the card edge, where a plain Row would just let it run off rather than wrap.
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceXs),
            horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm),
        ) {
            Text(
                text = reading.display,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = VitalType.BLOOD_SUGAR.unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceXs))

        Text(
            text = "Recorded at ${reading.recordedAt.format(recordedAtFormatter)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        // Colour is never the only signal here — the icon and the words carry the same
        // meaning independently, so a user who can't distinguish the colours loses nothing.
        StatusPill(
            text = status.label,
            icon = status.icon,
            contentColor = status.contentColor,
            containerColor = status.containerColor,
        )
    }
}

/** Small, self-contained description of a status badge: text + icon + colour, always together. */
private data class VitalStatus(
    val label: String,
    val icon: ImageVector,
    val contentColor: Color,
    val containerColor: Color,
)

private fun bloodSugarStatus(reading: VitalReading): VitalStatus =
    if (reading.isOutsideNormalRange) {
        VitalStatus(
            label = "A little outside your usual range",
            icon = Icons.Rounded.Info,
            contentColor = CareColors.Concern,
            containerColor = CareColors.ConcernSurface,
        )
    } else {
        VitalStatus(
            label = "Right in your normal range",
            icon = Icons.Rounded.CheckCircle,
            contentColor = CareColors.Good,
            containerColor = CareColors.GoodSurface,
        )
    }

// ---------------------------------------------------------------------------
// Cara's plain-language trend note
// ---------------------------------------------------------------------------

/**
 * Cara's read of the blood sugar trend, derived from the data rather than hardcoded — if the
 * mock data changes, this still describes it honestly.
 *
 * The approach, in plain terms: compare the *start* of the window to its *peak* to its *end*.
 * - A clear rise from start to a mid-window peak, followed by a clear fall back down by the
 *   end, reads as "went up, then came back down" — the exact shape of Margaret's data.
 * - A steady climb with no fall-back reads as "still drifting up".
 * - Little movement throughout reads as "steady".
 * - Anything else (e.g. genuinely choppy data) falls back to a calm, honest catch-all.
 *
 * Deliberately qualitative — no numbers are read out. Cara explains what happened, not what
 * the spreadsheet says.
 */
internal fun describeBloodSugarTrend(sortedReadings: List<VitalReading>): String {
    if (sortedReadings.size < 3) {
        return "There's not quite enough here yet for Cara to spot a pattern, that'll " +
            "change after a few more readings."
    }

    val values = sortedReadings.map { it.value }
    val n = values.size

    // "Early" and "late" windows of roughly a third of the data each, so a single noisy
    // reading at either end can't flip the whole read. At least one reading, at most two.
    val windowSize = (n / 3).coerceIn(1, 2)
    val earlyAverage = values.take(windowSize).average().toFloat()
    val lateAverage = values.takeLast(windowSize).average().toFloat()

    val peakValue = values.max()
    val peakIndex = values.indexOf(peakValue)
    // "In the middle" means the peak sits away from both edges — not just the first or last
    // reading, which would really just be a rising or falling line, not a rise-then-fall.
    val peakIsMidWindow = peakIndex >= windowSize && peakIndex < n - windowSize

    val roseFromStart = peakValue - earlyAverage > 0.6f
    val fellFromPeak = peakValue - lateAverage > 0.4f
    val stillElevated = lateAverage - earlyAverage > 0.6f
    val staysFlat = (values.max() - values.min()) <= 0.8f

    return when {
        roseFromStart && fellFromPeak && peakIsMidWindow ->
            "Your readings drifted up in the middle of this stretch and have come back " +
                "down over the last couple of days. Nothing to worry about."

        stillElevated ->
            "Your readings have been gently climbing recently. Nothing alarming, but " +
                "worth a mention next time you see your doctor."

        staysFlat ->
            "Your readings have stayed nice and steady over the last two weeks."

        else ->
            "Your readings have moved around a little, but they're settling into a normal " +
                "rhythm for you."
    }
}

@Composable
private fun CaraTrendNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        // The still Loop, standing in for Cara's voice — this is her interpretation, not a
        // statistic, and the mark makes that association without needing an extra label.
        LoopMark(size = CareDimens.LoopSmall, animated = false)
        Spacer(Modifier.width(CareDimens.SpaceMd))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = CareDimens.SpaceXs),
        )
    }
}

// ---------------------------------------------------------------------------
// The blood sugar trend chart
// ---------------------------------------------------------------------------

/**
 * A hand-drawn line-and-area chart of blood sugar over time.
 *
 * There is no charting library in this project, so every coordinate below is computed by
 * hand. The comments walk through the maths step by step because the shape of this data
 * (and therefore some of the constants) will change once real readings replace the mock set.
 */
@Composable
private fun BloodSugarTrendChart(
    readings: List<VitalReading>,
    modifier: Modifier = Modifier,
) {
    if (readings.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Not enough readings yet to draw a trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Styles and colours must be read from MaterialTheme/CareColors here, in composable
    // scope — the Canvas draw block below is a plain (non-composable) lambda and cannot
    // call MaterialTheme.* itself.
    //
    // bodyMedium (17sp), not the smaller labelMedium, deliberately — these axis labels are
    // how Margaret checks whether a reading is normal, not decorative metadata, so they get
    // the same "large and legible" treatment as everything else on this screen.
    val axisLabelStyle = MaterialTheme.typography.bodyMedium.copy(color = CareColors.Slate)
    val bandLabelStyle = MaterialTheme.typography.bodyMedium.copy(color = CareColors.Good)
    val textMeasurer = rememberTextMeasurer()

    val lineColor = CareColors.Navy
    // The area under the line is deliberately faint. Seen on a device, a 0.20
    // alpha fill sat on top of the normal-range band and hid it wherever the
    // readings ran high, which is precisely where someone needs to see whether
    // they have left the range. The band is the information; the area fill is
    // only there to give the line some weight, so the band wins.
    val areaTopColor = CareColors.Navy.copy(alpha = 0.09f)
    val areaBottomColor = CareColors.Navy.copy(alpha = 0.01f)
    val normalBandColor = CareColors.Good.copy(alpha = 0.18f)
    val gridColor = CareColors.Cloud
    val outsideColor = CareColors.Concern
    val markerRingColor = CareColors.White

    val normalRange = VitalType.BLOOD_SUGAR.normalRange
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

    Canvas(modifier = modifier) {
        // ---- 1. Reserve space for axis labels around a smaller inner "plot" rectangle ----
        // Generous margins because the labels are set at bodyMedium (17sp), not a tiny
        // caption size — see the comment above on why.
        val leftAxisWidth = 54.dp.toPx()
        val bottomAxisHeight = 34.dp.toPx()
        val topPadding = 14.dp.toPx()
        val rightPadding = 6.dp.toPx()

        val plotLeft = leftAxisWidth
        val plotTop = topPadding
        val plotWidth = size.width - leftAxisWidth - rightPadding
        val plotHeight = size.height - topPadding - bottomAxisHeight
        val plotBottom = plotTop + plotHeight

        // ---- 2. Work out the value range (y-axis domain) ----
        // The domain always stretches to cover both the data AND the normal range, with a
        // little breathing room, so the shaded band is never clipped even on a week where
        // every reading happens to sit outside it.
        val values = readings.map { it.value }
        val dataMin = values.min()
        val dataMax = values.max()
        val domainMin = floor(minOf(dataMin, normalRange.start) - 0.3f)
        val domainMax = ceil(maxOf(dataMax, normalRange.endInclusive) + 0.3f)
        val domainSpan = (domainMax - domainMin).takeIf { it > 0f } ?: 1f

        // Maps a reading value to a y-pixel coordinate. Higher values draw higher on screen,
        // so this is inverted: domainMax -> plotTop, domainMin -> plotBottom.
        fun yFor(value: Float): Float =
            plotTop + plotHeight * (1f - (value - domainMin) / domainSpan)

        // Maps a reading's position in the list to an x-pixel coordinate, spread evenly
        // across the plot width. Guards against a single-point list (division by zero).
        val lastIndex = (readings.size - 1).coerceAtLeast(1)
        fun xFor(index: Int): Float = plotLeft + plotWidth * (index.toFloat() / lastIndex)

        val points = readings.mapIndexed { index, reading -> Offset(xFor(index), yFor(reading.value)) }

        // ---- 3. The shaded "normal range" band ----
        val bandTop = yFor(normalRange.endInclusive)
        val bandBottom = yFor(normalRange.start)
        drawRect(
            color = normalBandColor,
            topLeft = Offset(plotLeft, bandTop),
            size = Size(plotWidth, bandBottom - bandTop),
        )
        drawText(
            textLayoutResult = textMeasurer.measure(AnnotatedString("Normal range"), bandLabelStyle),
            topLeft = Offset(plotLeft + 6.dp.toPx(), bandTop + 4.dp.toPx()),
        )

        // ---- 4. Y-axis gridlines and labels ----
        // Three evenly-spaced values: the bottom, middle, and top of the domain. Enough to
        // anchor the eye without cluttering a chart this small.
        val gridValues = listOf(domainMin, (domainMin + domainMax) / 2f, domainMax)
        for (gridValue in gridValues) {
            val y = yFor(gridValue)
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotLeft + plotWidth, y),
                strokeWidth = 1.5.dp.toPx(),
            )
            val label = "%.1f".format(gridValue)
            val measured = textMeasurer.measure(AnnotatedString(label), axisLabelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = plotLeft - 8.dp.toPx() - measured.size.width,
                    y = y - measured.size.height / 2f,
                ),
            )
        }

        // ---- 5. The smoothed line + shaded area beneath it ----
        // Simple, robust smoothing: between each pair of points, drop a cubic Bezier whose
        // control points sit directly above/below the midpoint x. This turns sharp zig-zags
        // into gentle S-curves without ever overshooting past a point's own y-value, so the
        // curve can't visually imply a reading that was never taken.
        //
        // The line and the area share the same curve, so it's built once here and reused —
        // the area path just continues on down to the baseline and closes the shape.
        fun smoothCurve(pts: List<Offset>): Path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val start = pts[i]
                val end = pts[i + 1]
                val midX = (start.x + end.x) / 2f
                cubicTo(midX, start.y, midX, end.y, end.x, end.y)
            }
        }
        val linePath = smoothCurve(points)
        val areaPath = smoothCurve(points).apply {
            lineTo(points.last().x, plotBottom)
            lineTo(points.first().x, plotBottom)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(areaTopColor, areaBottomColor),
                startY = plotTop,
                endY = plotBottom,
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ---- 6. Markers — a different SHAPE (not just a colour) for out-of-range points ----
        val markerRadius = 5.dp.toPx()
        val diamondHalfSize = 7.dp.toPx()
        readings.forEachIndexed { index, reading ->
            val center = points[index]
            if (reading.isOutsideNormalRange) {
                val diamond = Path().apply {
                    moveTo(center.x, center.y - diamondHalfSize)
                    lineTo(center.x + diamondHalfSize, center.y)
                    lineTo(center.x, center.y + diamondHalfSize)
                    lineTo(center.x - diamondHalfSize, center.y)
                    close()
                }
                drawPath(diamond, color = outsideColor)
                drawPath(diamond, color = markerRingColor, style = Stroke(width = 2.dp.toPx()))
            } else {
                drawCircle(color = lineColor, radius = markerRadius, center = center)
                drawCircle(
                    color = markerRingColor,
                    radius = markerRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // ---- 7. X-axis: just the start and end dates, to keep this legible at a glance ----
        val startLabel = readings.first().recordedAt.toLocalDate().format(dateFormatter)
        val endLabel = readings.last().recordedAt.toLocalDate().format(dateFormatter)
        val startMeasured = textMeasurer.measure(AnnotatedString(startLabel), axisLabelStyle)
        val endMeasured = textMeasurer.measure(AnnotatedString(endLabel), axisLabelStyle)

        drawText(
            textLayoutResult = startMeasured,
            topLeft = Offset(
                x = (points.first().x - startMeasured.size.width / 2f).coerceAtLeast(0f),
                y = plotBottom + 6.dp.toPx(),
            ),
        )
        drawText(
            textLayoutResult = endMeasured,
            topLeft = Offset(
                x = (points.last().x - endMeasured.size.width / 2f)
                    .coerceAtMost(size.width - endMeasured.size.width),
                y = plotBottom + 6.dp.toPx(),
            ),
        )
    }
}

@Composable
private fun ChartLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
    ) {
        LegendRow(
            swatch = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(CareColors.Good.copy(alpha = 0.35f)),
                )
            },
            label = "Your normal range",
        )
        LegendRow(
            swatch = { DiamondSwatch() },
            label = "Outside your normal range",
        )
    }
}

@Composable
private fun LegendRow(
    swatch: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiamondSwatch(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val half = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val diamond = Path().apply {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y)
            lineTo(center.x, center.y + half)
            lineTo(center.x - half, center.y)
            close()
        }
        drawPath(diamond, color = CareColors.Concern)
    }
}

// ---------------------------------------------------------------------------
// Adherence, day by day
// ---------------------------------------------------------------------------
//
// A dot-per-day strip, not a donut. Stacked/donut/radial charts are the shapes research on
// this cohort is clearest are BAD — a ring of taken/missed segments asks the reader to judge
// arc lengths, which is exactly the comparison older eyes do worst at. A row of one dot per
// day is closer to a calendar than a chart, and it reuses [CheckIn.status], which already
// exists per day, rather than inventing a second adherence field the reasoning engine would
// have to keep in sync with.

/** One glyph + colour per day, reused by both the strip's dots and the legend's swatches. */
private data class AdherenceDotStyle(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
)

/**
 * [CheckInStatus.ESCALATED] reads the same as [CheckInStatus.MISSED_DOSE] here on purpose —
 * both mean "a dose needed attention that day" — the strip is not the place to relitigate
 * the warmer "Cara let Sarah know" framing [HistoryScreen] uses; that distinction belongs to
 * the call record, not a glance-sized dot.
 */
private fun adherenceDotStyle(status: CheckInStatus): AdherenceDotStyle = when (status) {
    CheckInStatus.COMPLETED -> AdherenceDotStyle(Icons.Rounded.CheckCircle, CareColors.Good, CareColors.GoodSurface)
    CheckInStatus.MISSED_DOSE -> AdherenceDotStyle(Icons.Rounded.Info, CareColors.Concern, CareColors.ConcernSurface)
    CheckInStatus.ESCALATED -> AdherenceDotStyle(Icons.Rounded.Info, CareColors.Concern, CareColors.ConcernSurface)
    CheckInStatus.NO_ANSWER -> AdherenceDotStyle(Icons.Rounded.Phone, CareColors.Slate, CareColors.Cloud)
}

@Composable
private fun AdherenceDotIcon(status: CheckInStatus, size: Dp, modifier: Modifier = Modifier) {
    val style = adherenceDotStyle(status)
    Box(
        modifier = modifier
            .size(size)
            .background(style.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            style.icon,
            contentDescription = null,
            tint = style.tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

private val dayLabelFormatter: TextStyle = TextStyle.SHORT

@Composable
private fun AdherenceDay(checkIn: CheckIn, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AdherenceDotIcon(status = checkIn.status, size = 40.dp)
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            text = checkIn.startedAt.dayOfWeek.getDisplayName(dayLabelFormatter, Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The most recent seven check-ins, oldest first, so the strip reads left-to-right like a
 * week on a calendar rather than most-recent-first like [HistoryScreen]'s list.
 */
@Composable
private fun AdherenceStrip(checkIns: List<CheckIn>, modifier: Modifier = Modifier) {
    val lastWeek = checkIns.sortedByDescending { it.startedAt }.take(7).sortedBy { it.startedAt }

    if (lastWeek.isEmpty()) {
        Text(
            text = "Cara's first check-in will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        lastWeek.forEach { checkIn -> AdherenceDay(checkIn) }
    }
}

/**
 * [FlowRow], not [Row]: three labelled entries side by side fit at normal text size but not
 * at 200%, where a plain [Row] would let "No answer" run past the card edge instead of
 * wrapping onto its own line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdherenceLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm),
    ) {
        LegendRow(swatch = { AdherenceDotIcon(CheckInStatus.COMPLETED, size = 18.dp) }, label = "Taken")
        LegendRow(swatch = { AdherenceDotIcon(CheckInStatus.MISSED_DOSE, size = 18.dp) }, label = "Missed")
        LegendRow(swatch = { AdherenceDotIcon(CheckInStatus.NO_ANSWER, size = 18.dp) }, label = "No answer")
    }
}

// ---------------------------------------------------------------------------
// Blood pressure list
// ---------------------------------------------------------------------------

private val bpDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
private fun BloodPressureRow(
    reading: VitalReading,
    modifier: Modifier = Modifier,
) {
    val outside = reading.isOutsideNormalRange

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CareDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = if (outside) CareColors.Concern else CareColors.Navy,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reading.recordedAt.toLocalDate().format(bpDateFormatter),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (outside) {
                Text(
                    text = "A little higher than usual",
                    style = MaterialTheme.typography.labelMedium,
                    color = CareColors.Concern,
                )
            }
        }

        Text(
            text = "${reading.display} ${VitalType.BLOOD_PRESSURE.unit}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 400, heightDp = 1400)
@Composable
private fun VitalsScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        VitalsScreenContent(
            bloodSugarReadings = MockData.bloodSugarReadings,
            bloodPressureReadings = MockData.bloodPressureReadings,
            checkIns = MockData.checkIns,
            onRecordReading = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1400, name = "Dark")
@Composable
private fun VitalsScreenDarkPreview() {
    CareLoopTheme(darkTheme = true) {
        VitalsScreenContent(
            bloodSugarReadings = MockData.bloodSugarReadings,
            bloodPressureReadings = MockData.bloodPressureReadings,
            checkIns = MockData.checkIns,
            onRecordReading = {},
        )
    }
}

/** Confirms the adherence strip's legend and the today-reading FlowRow survive 200% scale. */
@PreviewFontScale
@Composable
private fun VitalsScreenFontScalePreview() {
    CareLoopTheme(darkTheme = false) {
        VitalsScreenContent(
            bloodSugarReadings = MockData.bloodSugarReadings,
            bloodPressureReadings = MockData.bloodPressureReadings,
            checkIns = MockData.checkIns,
            onRecordReading = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 360, name = "Chart only")
@Composable
private fun BloodSugarTrendChartPreview() {
    CareLoopTheme(darkTheme = false) {
        CareCard {
            BloodSugarTrendChart(
                readings = MockData.bloodSugarReadings.sortedBy { it.recordedAt },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }
    }
}
