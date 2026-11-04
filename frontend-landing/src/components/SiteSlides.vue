<script setup lang="ts">
import { computed } from 'vue';
import HowToPlay from '@/components/HowToPlay.vue';
import PlayLink from '@/components/PlayLink.vue';
import { go, MINE, story } from '@/store';

const on = (i: number) => story.phase === 'site' && story.node === i && !(i === 0 && story.focus >= 0);
const cls = (i: number) => ({ on: on(i), before: i < story.node });
const count = computed(() => MINE);
const shot = import.meta.env.BASE_URL + 'clips/about.webp';
</script>

<template>
  <!-- 第 0 屏：星河大厅。陌生人第一眼先知道这是什么，再去悬停看读者 -->
  <section class="slide hub" :class="cls(0)" :inert="!on(0)" data-name="星河">
    <div class="inner">
      <div>
        <div class="label">星流读者 — 网页文字冒险</div>
        <h1>每个人读到的，<br>都是自己的那一部。</h1>
      </div>
      <div class="hub-side">
        <p>世界变成了一部小说，你的每个选择都会写进正文，这部小说没有终章。<template v-if="story.choice >= 0">金色那一股是你刚写下的第一章。</template>旁边是 {{ count }} 部示例小说，<span class="mouse-only">把鼠标移到任意一道文字流上看看，点一下飞进去读。</span><span class="touch-only">点任意一道文字流看看。</span></p>
        <button class="more" type="button" @click="go(1)">这是什么 <span>↓</span></button>
      </div>
    </div>
  </section>

  <section class="slide info-slide" :class="cls(1)" :inert="!on(1)" data-name="这是什么">
    <div class="inner scroller">
      <div class="info-block">
        <div class="about">
          <div>
        <div class="label">01 — 这是什么</div>
        <h2 class="info-title">世界变成了一部小说，<br>而你读过它。</h2>
        <p class="info-lead">星流读者是一款网页文字冒险。现实被改写成一部连载小说，城市变成一个个「剧本场景」，星座们通过「星流广播」观看每一位读者。你知道故事原本会怎么写——但从第一章开始，后面怎么写由你决定，而且一直写下去。</p>
          </div>
          <figure class="game-frame about-shot"><img :src="shot" alt="星流读者的游戏界面：探索记录和奖励" loading="lazy"><figcaption>真实游戏画面</figcaption></figure>
        </div>
        <dl class="facts">
          <div><dt>题材</dt><dd>文字冒险 · 分支剧情</dd></div>
          <div><dt>平台</dt><dd>浏览器即玩，手机电脑都行</dd></div>
          <div><dt>核心</dt><dd>你的每个选择都会写进正文</dd></div>
        </dl>
      </div>
    </div>
  </section>

  <section class="slide info-slide" :class="cls(2)" :inert="!on(2)" data-name="怎么玩">
    <div class="inner scroller">
      <div class="info-block">
        <div class="label">02 — 怎么玩</div>
        <h2 class="info-title">一局，四步。</h2>
        <HowToPlay :node="2" />
      </div>
    </div>
  </section>

  <section class="slide info-slide" :class="cls(3)" :inert="!on(3)" data-name="核心玩法">
    <div class="inner scroller">
      <div class="info-block">
        <div class="label">03 — 核心玩法</div>
        <h2 class="info-title">让每一部小说，<br>都不一样。</h2>
        <div class="systems">
          <article><span>星座赞助</span><h3>有人在看着你。</h3><p>开局选择一位星座。他会关注你的表现，频道热度越高，你越受瞩目。</p></article>
          <article><span>称号</span><h3>称号不是头衔，是玩法。</h3><p>每个称号都会真实改变数值：行动力上限、防御、剧情出现的概率。倒下时，可以献上一个称号换回一条命。</p></article>
          <article><span>位阶</span><h3>从临时化身，到全知超越者。</h3><p>F 到 SSS 共九个位阶，每一阶都有明确的晋升条件。位阶越高，成长越快。</p></article>
          <article><span>世界线</span><h3>所有人共享一条世界线。</h3><p>全服读者的行动一起推动世界线偏移。偏移越高，奖励和事件越多；它也会随时间慢慢回落。</p></article>
          <article><span>频道与援救</span><h3>你不是一个人在读。</h3><p>实时频道里能看到其他读者的动向。有人倒在冥界时，你可以伸手把他拉回来。</p></article>
          <article><span>图鉴与分享</span><h3>把你的小说给别人看。</h3><p>路线图鉴记下你到过的每一条分支，和那些还写着「待续」的枝尖。你的路线可以生成链接，朋友不用登录就能从第一章读起。</p></article>
        </div>

      </div>
    </div>
  </section>

  <section class="slide end" :class="cls(4)" :inert="!on(4)" data-name="开始">
    <div class="inner">
      <div class="label" style="justify-content:center">04 — 开始阅读</div>
      <h2>第一章，已经开始了。</h2>
      <p>没有终章，只有下一章。</p>
      <PlayLink text="开始阅读" />
      <footer>© 星流读者 · 原创文字冒险 · 页面中的读者故事均为示例</footer>
    </div>
  </section>
</template>
