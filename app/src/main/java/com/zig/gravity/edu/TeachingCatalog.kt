package com.zig.gravity.edu

data class CardContent(val t1: String, val t2: String, val t3: String)

object TeachingCatalog {
    val cardsFa: Map<String, CardContent> = mapOf(
        "orbit" to CardContent(
            t1 = "این جسم دور مرکز گرانش، مسیرِ بسته‌ای می‌چرخد — یک مدار ساخته شد.",
            t2 = "جسم دائماً در حال سقوط است، اما چون به‌اندازهٔ کافی تند به پهلو می‌رود، هر بار از کنار مرکز گرانش رد می‌شود و به آن نمی‌رسد. این «سقوطِ همیشگی» همان چیزی است که به آن مدار می‌گوییم.",
            t3 = "طبق قوانین کپلر، هر چه مدار بزرگ‌تر باشد، یک دورِ کامل کندتر طول می‌کشد: T² ∝ a³. خودت امتحان کن: سرعت این جسم را کمی کم کن و ببین به سمت مرکز سقوط می‌کند."
        ),
        "merge" to CardContent(
            t1 = "دو جسم به هم رسیدند و با هم یکی شدند.",
            t2 = "اجسام آسمانی در برخورد معمولاً مثل توپ نمی‌جهند؛ به هم می‌چسبند و بزرگ‌تر می‌شوند. تکانهٔ هر دو جسم در جسمِ تازه حفظ می‌شود.",
            t3 = "جرمِ تازه دقیقاً مجموع دو جرم است و سرعتش از قانون پایستگی تکانه می‌آید: v = (m₁v₁ + m₂v₂)/(m₁ + m₂). ماه ما از برخوردهای بزرگِ همین‌چنینی زاده شده است!"
        ),
        "capture" to CardContent(
            t1 = "این جسم از افق رویداد گذشت — دیگر هیچ راه برگشتی نیست.",
            t2 = "سیاه‌چاله آن‌قدر جرم در فضایی کوچک جمع شده که هیچ چیز، حتی نور، از مرزی به نام «افق رویداد» نمی‌تواند بیرون بیاید. این جسم برای همیشه بخشی از سیاه‌چاله شد.",
            t3 = "افق رویدادِ واقعیِ این سیاه‌چاله فقط چند کیلومتر است — در بازرسِ جسم ببین. یادت باشد: موتور این شبیه‌سازی گرانش را نیوتنی حساب می‌کند؛ نسبیت فقط در همین توضیح است."
        ),
        "wormhole" to CardContent(
            t1 = "جسم از یک دروازه وارد شد و از دیگری بیرون آمد — با همان سرعت.",
            t2 = "کرم‌چاله راه‌حلی ریاضی از معادلات نسبیت عام است، اما هیچ کرم‌چالهٔ قابل‌گذری تا امروز مشاهده نشده است. این یک آزمایشِ فرضی است.",
            t3 = "راه‌حل‌های شناخته‌شده به «مادهٔ عجیب» با انرژی منفی نیاز دارند. حفظِ سرعت و جهت هنگام عبور، انتخابِ مدل‌سازی ما است، نه فیزیکِ اثبات‌شده."
        ),
        "escape" to CardContent(
            t1 = "این جسم از چنگ گرانش آزاد شد و دیگر برنمی‌گردد.",
            t2 = "سرعتش از «سرعت گریز» گذشت. آن‌سوی این مرز، جاذبه هنوز هست، اما برای نگه‌داشتن جسم کافی نیست.",
            t3 = "سرعت گریز از فاصلهٔ r برابر است با v = √(2GM/r) — نزدیک زمین حدود ۱۱٫۲ کیلومتر بر ثانیه. فضاپیماها دقیقاً همین‌طور از زمین دل می‌کَنند."
        ),
        "decay" to CardContent(
            t1 = "مدار این جسم دارد تنگ‌تر می‌شود — در راه برخورد.",
            t2 = "مدار از خودش نمی‌شکند؛ چیزی انرژی‌اش را گرفته است: آشوبِ یک جسم سوم، یا دستِ خودت وقتی سرعت را کم کردی.",
            t3 = "ماهواره‌های کم‌ارتفاعِ واقعی به‌خاطر اصطکاک جوّ مدارشان می‌افتد — همان دلیلی که ایستگاه فضایی باید مدام سوخت بسوزاند و بالا بماند."
        ),
        "dance" to CardContent(
            t1 = "این دو به گردِ هم می‌چرخند — هر دو، گردِ نقطهٔ وسط.",
            t2 = "هیچ‌کدام دیگری را نمی‌چرخاند! هر دو گردِ «مرکز جرمِ» مشترک می‌گردند و جسمِ سنگین‌تر مسیرِ کوچک‌تری می‌رود.",
            t3 = "بسیاری از ستاره‌های آسمان جفت‌اند؛ سیریوس، درخشان‌ترین ستارهٔ شب، همدمی دارد که گردِ هم می‌چرخند."
        )
    )

    val cardsEn: Map<String, CardContent> = mapOf(
        "orbit" to CardContent(
            t1 = "This body now follows a closed path around its gravity center — an orbit was born.",
            t2 = "It is falling constantly, but it is also moving sideways fast enough to keep missing the center. This 'perpetual fall' is what we call an orbit.",
            t3 = "By Kepler's laws, bigger orbits take longer per lap: T² ∝ a³. Try it yourself: reduce this body's speed a little and watch it fall inward."
        ),
        "merge" to CardContent(
            t1 = "Two bodies met and became one.",
            t2 = "Celestial collisions don't usually bounce; they stick and grow. The momentum of both bodies lives on in the new one.",
            t3 = "The new mass is exactly the sum, and the new velocity comes from momentum conservation: v = (m₁v₁ + m₂v₂)/(m₁ + m₂). Our Moon was born from a giant impact like this!"
        ),
        "capture" to CardContent(
            t1 = "That body crossed the event horizon — there is no way back now.",
            t2 = "A black hole packs so much mass into so little space that nothing, not even light, can cross back out past the boundary called the event horizon. That body is now part of the black hole forever.",
            t3 = "This black hole's TRUE horizon is only a few kilometers wide — see it in the inspector. Remember: this simulation computes gravity Newtonianly; relativity lives only in this explanation."
        ),
        "wormhole" to CardContent(
            t1 = "The body entered one gate and came out the other — same speed.",
            t2 = "A wormhole is a mathematical solution of general relativity, but no traversable wormhole has ever been observed. This is a hypothetical experiment.",
            t3 = "Known solutions require 'exotic matter' with negative energy. Preserving speed and direction through the gate is our modeling choice, not established physics."
        ),
        "escape" to CardContent(
            t1 = "This body broke free of gravity and is never coming back.",
            t2 = "Its speed passed the 'escape velocity'. Beyond that line, gravity still pulls, but it can no longer hold the body.",
            t3 = "Escape speed from distance r is v = √(2GM/r) — about 11.2 km/s near Earth. This is exactly how spacecraft leave home."
        ),
        "decay" to CardContent(
            t1 = "This orbit is tightening — heading for a collision.",
            t2 = "Orbits don't decay by themselves; something took energy: the chaos of a third body, or your own hand reducing the speed.",
            t3 = "Real low-orbit satellites decay through atmospheric drag — the reason the space station must fire engines to stay up."
        ),
        "dance" to CardContent(
            t1 = "These two circle each other — both around the midpoint.",
            t2 = "Neither one orbits the other! Both circle their shared center of mass, and the heavier one takes the smaller path.",
            t3 = "Many real stars are pairs; Sirius, the brightest star of night, has a companion dancing with it."
        )
    )

    fun getCard(cardId: String, isEnglish: Boolean = false): CardContent? {
        val map = if (isEnglish) cardsEn else cardsFa
        return map[cardId] ?: (if (isEnglish) cardsFa[cardId] else cardsEn[cardId])
    }
}
