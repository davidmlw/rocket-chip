// See LICENSE.SiFive for license details.

// If you know two clocks are related with a N:1 or 1:N relationship, you
// can cross the clock domains with lower latency than an AsyncQueue.
// This clock crossing behaves almost identically to a TLBuffer(2):
//   - It adds one cycle latency to each clock domain.
//   - All outputs of TLRational are registers (bits, valid, and ready).
//   - It costs 3*bits registers as opposed to 2*bits in a TLBuffer(2)

package freechips.rocketchip.tilelink

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

/** Synthesizeable unit tests */
import freechips.rocketchip.unittest._

class TLRAMRationalCrossingSource(name: String, txns: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLRationalIdentityNode()
  val fuzz  = LazyModule(new TLFuzzer(txns))
  val model = LazyModule(new TLRAMModel(name))

  (node
    := TLRationalCrossingSource()
    := TLDelayer(0.25)
    := model.node
    := fuzz.node)

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val finished = Bool(OUTPUT)
    })
    io.finished := fuzz.module.io.finished
  }
}

class TLRAMRationalCrossingSink(direction: RationalDirection)(implicit p: Parameters) extends LazyModule {
  val node = TLRationalIdentityNode()
  val ram  = LazyModule(new TLRAM(AddressSet(0x0, 0x3ff)))

  (ram.node
    := TLFragmenter(4, 256)
    := TLDelayer(0.25)
    := TLRationalCrossingSink(direction)
    := node)

  lazy val module = new LazyModuleImp(this) { }
}

class TLRAMRationalCrossing(txns: Int)(implicit p: Parameters) extends LazyModule {
  val sym_fast_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing sym_fast", txns))
  val sym_slow_sink   = LazyModule(new TLRAMRationalCrossingSink(Symmetric))
  sym_slow_sink.node := sym_fast_source.node

  val sym_slow_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing sym_slow", txns))
  val sym_fast_sink   = LazyModule(new TLRAMRationalCrossingSink(Symmetric))
  sym_fast_sink.node := sym_slow_source.node

  val fix_fast_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing fast", txns))
  val fix_slow_sink   = LazyModule(new TLRAMRationalCrossingSink(FastToSlow))
  fix_slow_sink.node := fix_fast_source.node

  val fix_slow_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing slow", txns))
  val fix_fast_sink   = LazyModule(new TLRAMRationalCrossingSink(SlowToFast))
  fix_fast_sink.node := fix_slow_source.node

  lazy val module = new LazyModuleImp(this) with UnitTestModule {
    io.finished :=
      sym_fast_source.module.io.finished &&
      sym_slow_source.module.io.finished &&
      fix_fast_source.module.io.finished &&
      fix_slow_source.module.io.finished

    // Generate faster clock (still divided so verilator approves)
    val fast = Module(new Pow2ClockDivider(1))
    sym_fast_source.module.clock := fast.io.clock_out
    sym_fast_sink  .module.clock := fast.io.clock_out
    fix_fast_source.module.clock := fast.io.clock_out
    fix_fast_sink  .module.clock := fast.io.clock_out

    // Generate slower clock
    val slow = Module(new Pow2ClockDivider(2))
    fix_slow_source.module.clock := slow.io.clock_out
    fix_slow_sink  .module.clock := slow.io.clock_out

    val odd = Module(new ClockDivider3)
    odd.io.clk_in := clock
    sym_slow_source.module.clock := odd.io.clk_out
    sym_slow_sink  .module.clock := odd.io.clk_out
  }
}

class TLRAMRationalCrossingTest(txns: Int = 5000, timeout: Int = 500000)(implicit p: Parameters) extends UnitTest(timeout) {
  val dut = Module(LazyModule(new TLRAMRationalCrossing(txns)).module)
  io.finished := dut.io.finished
}
