// See LICENSE.SiFive for license details.

package freechips.rocketchip.tilelink

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

/** Synthesizeable integration test */
import freechips.rocketchip.unittest._

class TLFuzzRAM(txns: Int)(implicit p: Parameters) extends LazyModule
{
  val model = LazyModule(new TLRAMModel("TLFuzzRAM"))
  val ram  = LazyModule(new TLRAM(AddressSet(0x800, 0x7ff)))
  val ram2 = LazyModule(new TLRAM(AddressSet(0, 0x3ff), beatBytes = 16))
  val gpio = LazyModule(new RRTest1(0x400))
  val xbar = LazyModule(new TLXbar)
  val xbar2= LazyModule(new TLXbar)
  val fuzz = LazyModule(new TLFuzzer(txns))

  xbar2.node := TLAtomicAutomata() := model.node := fuzz.node
  ram2.node := TLFragmenter(16, 256) := xbar2.node
  xbar.node := TLWidthWidget(16) := TLHintHandler() := xbar2.node
  ram.node := TLFragmenter(4, 256) := TLBuffer() := xbar.node
  gpio.node := TLFragmenter(4, 32) := TLBuffer() := xbar.node

  lazy val module = new LazyModuleImp(this) with UnitTestModule {
    io.finished := fuzz.module.io.finished
  }
}

class TLFuzzRAMTest(txns: Int = 5000, timeout: Int = 500000)(implicit p: Parameters) extends UnitTest(timeout) {
  val dut = Module(LazyModule(new TLFuzzRAM(txns)).module)
  io.finished := dut.io.finished
}
