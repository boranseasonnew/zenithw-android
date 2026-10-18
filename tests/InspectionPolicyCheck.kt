package space.zenithw.app

fun main() {
    var count=0
    fun expect(value: Boolean) { check(value);count++ }
    fun rejects(block: ()->Unit) { expect(runCatching(block).isFailure) }
    expect(InspectionPolicy.socketTimeout(false)==8)
    expect(InspectionPolicy.totalTimeoutMillis(false)<InspectionPolicy.totalTimeoutMillis(true))
    expect(!InspectionPolicy.shouldCheckUpdate(false,true,10000,9000,24))
    expect(InspectionPolicy.shouldCheckUpdate(true,false,10000,9000,24))
    expect(InspectionPolicy.shouldCheckUpdate(false,true,86400001,1,24))
    expect(!InspectionPolicy.shouldCheckUpdate(false,false,86400001,1,24))
    expect(InspectionPolicy.shouldCheckUpdate(false,true,10,100,24))
    expect(InspectionPolicy.section("","")==null)
    expect(InspectionPolicy.section("1:02.5","2:00",150)=="*62.5-120.0")
    expect(InspectionPolicy.section("30","")=="*30.0-inf")
    expect(InspectionPolicy.section("","40")=="*0.0-40.0")
    rejects { InspectionPolicy.section("60","30") }
    rejects { InspectionPolicy.section("1:99","2:00") }
    rejects { InspectionPolicy.section("-1","10") }
    rejects { InspectionPolicy.section("100","120",90) }
    rejects { InspectionPolicy.section("0","120",90) }
    rejects { InspectionPolicy.section("NaN","10") }
    rejects { InspectionPolicy.section("9".repeat(400),"") }
    expect(InspectionPolicy.literalFilename("100% music")=="100%% music.%(ext)s")
    rejects { InspectionPolicy.literalFilename("../elsewhere") }
    rejects { InspectionPolicy.literalFilename("hidden\\path") }
    rejects { InspectionPolicy.literalFilename("a\nname") }
    rejects { InspectionPolicy.literalFilename("界".repeat(80)) }
    println("$count inspection and clip policy checks passed")
}
