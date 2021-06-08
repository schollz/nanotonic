// Engine_Supertonic

// Inherit methods from CroneEngine
Engine_Supertonic : CroneEngine {

    // Supertonic specific v0.1.0
    var synSupertonic;
    var synVoice=0;
    var maxVoices=7;
    // Supertonic ^

    *new { arg context, doneCallback;
        ^super.new(context, doneCallback);
    }

    alloc {
        // Supertonic specific v0.0.1
        SynthDef("supertonic", {
            arg out,
            mix=50,level=(-5),distAmt=2,
            eQFreq=632.4,eQGain=(-20),
            oscAtk=0,oscDcy=500,
            oscWave=0,oscFreq=54,
            modMode=0,modRate=400,modAmt=18,
            nEnvAtk=26,nEnvDcy=200,
            nFilFrq=1000,nFilQ=2.5,
            nFilMod=0,nEnvMod=0,nStereo=1,
            oscLevel=1,nLevel=1,
            oscVel=100,nVel=100,modVel=100,
            fx_lowpass_freq=20000,fx_lowpass_rq=1,
            vel=64;

            // variables
            var osc,noz,nozPostF,snd,pitchMod,nozEnv,numClaps,oscFreeSelf,wn1,wn2,clapFrequency,decayer;

            // convert to seconds from milliseconds
            vel=LinLin.kr(vel,0,128,0,2);
            oscAtk=DC.kr(oscAtk/1000);
            oscDcy=DC.kr(oscDcy/1000);
            nEnvAtk=DC.kr(nEnvAtk/1000);
            nEnvDcy=DC.kr(nEnvDcy/1000*1.4);
            level=DC.kr(level);
            // add logistic curve to the mix
            mix=DC.kr(100/(1+(2.7182**((50-mix)/8))));
            // this is important at low freq
            oscFreq=oscFreq+5;

            // white noise generators (expensive)
            // wn1=SoundIn.ar(0); 
            //wn2=SoundIn.ar(1);
            //wn1=wn2;
            wn1=WhiteNoise.ar();
            wn2=WhiteNoise.ar();
            wn1=Clip.ar(wn1*100,-1,1);
            wn2=Clip.ar(wn2*100,-1,1);
            clapFrequency=DC.kr((4311/(nEnvAtk*1000+28.4))+11.44); // fit using matlab
            // determine who should free
            oscFreeSelf=DC.kr(Select.kr(((oscAtk+oscDcy)>(nEnvAtk+nEnvDcy)),[0,2]));

            // define pitch modulation1
            pitchMod=Select.ar(modMode,[
                Decay.ar(Impulse.ar(0.0001),(1/(2*modRate))), // decay
                SinOsc.ar(-1*modRate), // sine
                Lag.ar(LFNoise0.ar(4*modRate),1/(4*modRate)), // random
            ]);

            // mix in the the pitch mod
            pitchMod=pitchMod*modAmt/2*(LinLin.kr(modVel,0,200,2,0)*vel);
            oscFreq=((oscFreq).cpsmidi+pitchMod).midicps;

            // define the oscillator
            osc=Select.ar(oscWave,[
                SinOsc.ar(oscFreq),
                LFTri.ar(oscFreq,mul:0.5),
                SawDPW.ar(oscFreq,mul:0.5),
            ]);
            osc=Select.ar(modMode>1,[
                osc,
                SelectX.ar(oscDcy<0.1,[
                    LPF.ar(wn2,modRate),
                    osc,
                ])
            ]);


            // add oscillator envelope
            decayer=SelectX.kr(distAmt/100,[0.05,distAmt/100*0.3]);
            osc=osc*EnvGen.ar(Env.new([0.0001,1,0.9,0.0001],[oscAtk,oscDcy*decayer,oscDcy],\exponential),doneAction:oscFreeSelf);

            // apply velocity
            osc=(osc*LinLin.kr(oscVel,0,200,1,0)*vel).softclip;

            // generate noise
            noz=wn1;

            // optional stereo noise
            noz=Select.ar(nStereo,[wn1,[wn1,wn2]]);


            // define noise envelope
            nozEnv=Select.ar(nEnvMod,[
                EnvGen.ar(Env.new(levels: [0.001, 1, 0.0001], times: [nEnvAtk, nEnvDcy],curve:\exponential),doneAction:(2-oscFreeSelf)),
                EnvGen.ar(Env.new([0.0001,1,0.9,0.0001],[nEnvAtk,nEnvDcy*decayer,nEnvDcy*(1-decayer)],\linear)),
                Decay.ar(Impulse.ar(clapFrequency),1/clapFrequency,0.85,0.15)*Trig.ar(1,nEnvAtk+0.001)+EnvGen.ar(Env.new(levels: [0.001, 0.001, 1,0.0001], times: [nEnvAtk,0.001, nEnvDcy],curve:\exponential)),
            ]);

            // apply noise filter
            nozPostF=Select.ar(nFilMod,[
                BLowPass.ar(noz,nFilFrq,Clip.kr(1/nFilQ,0.5,3)),
                BBandPass.ar(noz,nFilFrq,Clip.kr(2/nFilQ,0.1,6)),
                BHiPass.ar(noz,nFilFrq,Clip.kr(1/nFilQ,0.5,3))
            ]);
            // special Q
            nozPostF=SelectX.ar((0.1092*(nFilQ.log)+0.0343),[nozPostF,SinOsc.ar(nFilFrq)]);

            // apply envelope to noise
            noz=Splay.ar(nozPostF*nozEnv);

            // apply velocities
            noz=(noz*LinLin.kr(nVel,0,200,1,0)*vel).softclip;



            // mix oscillator and noise
            snd=SelectX.ar(mix/100*2,[
                noz*0.5,
                noz*2,
                osc*1
            ]);

            // apply distortion
            snd=SineShaper.ar(snd,1.0,1+(10/(1+(2.7182**((50-distAmt)/8))))).softclip;

            // apply eq after distortion
            snd=BPeakEQ.ar(snd,eQFreq,1,eQGain/2);

            snd=HPF.ar(snd,20);

            snd=snd*level.dbamp*0.2;

            // free self if its quiet
            FreeSelf.kr((Amplitude.kr(snd)<0.001)*TDelay.kr(DC.kr(1),0.03));

            // apply some global fx
            snd=RLPF.ar(snd,fx_lowpass_freq,fx_lowpass_rq);

            Out.ar(0, snd);
        }).add;

        context.server.sync;

        synSupertonic = Array.fill(maxVoices,{arg i;
            Synth("supertonic", [\level,-100],target:context.xg);
        });

        context.server.sync;

        this.addCommand("supertonic","ffffffffffffffffffffffffi", { arg msg;
            // lua is sending 1-index
            synVoice=synVoice+1;
            if (synVoice>(maxVoices-1),{synVoice=0});
            if (synSupertonic[synVoice].isRunning,{
                ("freeing "++synVoice).postln;
                synSupertonic[synVoice].free;
            });
            synSupertonic[synVoice]=Synth("supertonic",[
                \out,0,
                \distAmt, msg[1],
                \eQFreq, msg[2],
                \eQGain, msg[3],
                \level, msg[4],
                \mix, msg[5],
                \modAmt, msg[6],
                \modMode, msg[7],
                \modRate, msg[8],
                \nEnvAtk, msg[9],
                \nEnvDcy, msg[10],
                \nEnvMod, msg[11],
                \nFilFrq, msg[12],
                \nFilMod, msg[13],
                \nFilQ, msg[14],
                \nStereo, msg[15],
                \oscAtk, msg[16],
                \oscDcy, msg[17],
                \oscFreq, msg[18],
                \oscWave, msg[19],
                \oscVel, msg[20],
                \nVel, msg[21],
                \modVel, msg[22],
                \fx_lowpass_freq,msg[23],
                \fx_lowpass_rq,msg[24],
            ], target:context.server);
            NodeWatcher.register(synSupertonic[synVoice]);
        });

        this.addCommand("supertonic_lpf","iff",{ arg msg;
            synSupertonic[msg[1]-1].set(
                \fx_lowpass_freq,msg[2],
                \fx_lowpass_rq,msg[3],
            );
        });
        // ^ Supertonic specific

    }

    free {
        // Supertonic Specific v0.0.1
        (0..maxVoices).do({arg i; synSupertonic[i].free});
        // ^ Supertonic specific
    }
}
