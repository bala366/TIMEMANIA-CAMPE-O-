package br.com.timemaniacampeao;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    final int PURPLE=Color.rgb(93,28,153), GREEN=Color.rgb(23,139,67);
    TextView status, result; Button analyze, pdfButton; Uri inputUri;
    ArrayList<Draw> history=new ArrayList<>(); int[] ticket; String team=""; String report="";
    static class Draw { int id; int[] nums; String team; Draw(int i,int[] n,String t){id=i;nums=n;team=t;} }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,26,24,18);root.setBackgroundColor(Color.rgb(246,241,251));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        TextView heading=new TextView(this);heading.setText("♣ TIMEMANIA CAMPEÃO V1");heading.setTextSize(24);heading.setTextColor(PURPLE);heading.setTypeface(null,1);root.addView(heading);
        TextView desc=new TextView(this);desc.setText("80 dezenas • 10 blocos de 8 • 73 falhas por concurso • 1 repetida do último sorteio • Time do Coração");desc.setTextSize(15);root.addView(desc);
        Button pick=button("1. ESCOLHER HISTÓRICO TXT/CSV",root);pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);});
        analyze=button("2. INICIAR ANÁLISE",root);analyze.setEnabled(false);analyze.setOnClickListener(v->runAnalysis());
        pdfButton=button("3. SALVAR PDF DO VOLANTE",root);pdfButton.setEnabled(false);pdfButton.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,"TIMEMANIA_CAMPEAO_V1.pdf");startActivityForResult(i,2);});
        status=new TextView(this);status.setText("Selecione o arquivo com concurso - 7 dezenas - time.");status.setTextSize(15);status.setPadding(0,20,0,16);root.addView(status);
        result=new TextView(this);result.setTextSize(16);result.setTextColor(Color.BLACK);result.setTextIsSelectable(true);root.addView(result);
    }
    Button button(String s,LinearLayout root){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));root.addView(b);return b;}
    @Override protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(code!=RESULT_OK||data==null)return;
        if(req==1){inputUri=data.getData();status.setText("Arquivo selecionado. Clique em INICIAR ANÁLISE.");analyze.setEnabled(true);pdfButton.setEnabled(false);}
        if(req==2&&ticket!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){writePdf(out);status.setText("PDF salvo com sucesso.");}catch(Exception e){status.setText("Erro ao salvar PDF: "+e.getMessage());}}
    }
    void runAnalysis(){analyze.setEnabled(false);pdfButton.setEnabled(false);status.setText("Lendo resultados e auditando falhas...");result.setText("");new Thread(()->{try{
        ArrayList<Draw> ds=readHistory(); if(ds.size()<2)throw new Exception("Não foram encontrados pelo menos dois concursos válidos. Use linhas como: 1 - 24,31,51,57,63,71,80 - PALMAS/TO");
        history=ds;runOnUiThread(()->status.setText("Histórico carregado: "+ds.size()+" concursos. Calculando 85.000 candidatos..."));
        calculate();runOnUiThread(()->{result.setText(report);status.setText("Análise concluída. PDF disponível.");pdfButton.setEnabled(true);analyze.setEnabled(true);});
    }catch(Exception e){runOnUiThread(()->{status.setText("ERRO: "+e.getMessage());analyze.setEnabled(true);});}}).start();}
    ArrayList<Draw> readHistory()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(inputUri)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
        byte[] bytes=out.toByteArray();String raw=new String(bytes,StandardCharsets.UTF_8);if(raw.indexOf('\uFFFD')>=0)raw=new String(bytes,Charset.forName("windows-1252"));
        TreeMap<Integer,Draw> found=new TreeMap<>();Pattern pat=Pattern.compile("^\\s*(\\d+)\\s*[-–—]\\s*((?:\\d{1,2}\\s*[,;]\\s*){6}\\d{1,2})\\s*[-–—]\\s*(.+?)\\s*$");
        int rejected=0;for(String line:raw.split("\\R")){if(line.trim().isEmpty())continue;Matcher m=pat.matcher(line);if(!m.matches()){rejected++;continue;}String[] parts=m.group(2).split("[,;]");int[] nums=new int[7];HashSet<Integer> unique=new HashSet<>();boolean valid=true;for(int i=0;i<7;i++){nums[i]=Integer.parseInt(parts[i].trim());if(nums[i]<1||nums[i]>80||!unique.add(nums[i]))valid=false;}if(!valid){rejected++;continue;}Arrays.sort(nums);int id=Integer.parseInt(m.group(1));found.put(id,new Draw(id,nums,m.group(3).trim()));}
        if(found.size()<2)throw new Exception("Formato não reconhecido; "+rejected+" linhas rejeitadas.");return new ArrayList<>(found.values());
    }
    void calculate(){int N=history.size();int[] freq=new int[81],recent=new int[81],medium=new int[81],gap=new int[81],maxGap=new int[81],blockMiss=new int[10],blockRecent=new int[10];
        int[] lastSeen=new int[81];Arrays.fill(lastSeen,-1);int[] overlap=new int[8];int totalOverlap=0;int minOverlap=99,maxOverlap=0;
        for(int i=0;i<N;i++){Draw d=history.get(i);boolean[] present=new boolean[81];for(int x:d.nums){present[x]=true;freq[x]++;if(i>=N-30)recent[x]++;if(i>=N-100)medium[x]++;maxGap[x]=Math.max(maxGap[x],i-lastSeen[x]-1);lastSeen[x]=i;}
            for(int b=0;b<10;b++){boolean miss=true;for(int x=b*8+1;x<=b*8+8;x++)if(present[x]){miss=false;break;}if(miss){blockMiss[b]++;if(i>=N-30)blockRecent[b]++;}}
            if(i>0){boolean[] prev=new boolean[81];for(int x:history.get(i-1).nums)prev[x]=true;int bothAbsent=0;for(int x=1;x<=80;x++)if(!present[x]&&!prev[x])bothAbsent++;minOverlap=Math.min(minOverlap,bothAbsent);maxOverlap=Math.max(maxOverlap,bothAbsent);totalOverlap+=bothAbsent;}
        }
        double[] weights=new double[81];for(int x=1;x<=80;x++){gap[x]=N-1-lastSeen[x];maxGap[x]=Math.max(maxGap[x],gap[x]);int b=(x-1)/8;double persistence=.5*recent[x]/Math.min(N,30.)+.3*medium[x]/Math.min(N,100.)+.2*freq[x]/(double)N;double gapRatio=Math.min(2.,gap[x]/(double)Math.max(1,maxGap[x]));weights[x]=1000*(.40*persistence+.24*gapRatio/2+.20*blockRecent[b]/Math.min(N,30.)+.16*Math.min(1.,gap[x]/25.));}
        HashSet<Integer> last=new HashSet<>();for(int x:history.get(N-1).nums)last.add(x);ArrayList<Integer> pool=new ArrayList<>();for(int x=1;x<=80;x++)if(!last.contains(x))pool.add(x);pool.sort((a,b)->Double.compare(weights[b],weights[a]));
        Random rng=new Random(20261011L+history.get(N-1).id);int[] best=null;double bestPre=-1;for(int i=0;i<85000;i++){HashSet<Integer> s=new HashSet<>();s.add(history.get(N-1).nums[rng.nextInt(7)]);int limit=i<28333?Math.min(38,pool.size()):pool.size();while(s.size()<10)s.add(pool.get(rng.nextInt(limit)));int[] t=sorted(s);double score=preScore(t,weights);if(score>bestPre){bestPre=score;best=t;}}
        HashSet<String> unique=new HashSet<>();ArrayList<int[]> candidates=new ArrayList<>();candidates.add(best);unique.add(Arrays.toString(best));for(int i=0;i<2500;i++){int[] t=best.clone();int idx=rng.nextInt(10);boolean repeated=last.contains(t[idx]);ArrayList<Integer> options=new ArrayList<>();for(int x=1;x<=80;x++)if(last.contains(x)==repeated&&!contains(t,x))options.add(x);if(options.isEmpty())continue;t[idx]=options.get(rng.nextInt(options.size()));Arrays.sort(t);if(unique.add(Arrays.toString(t)))candidates.add(t);}
        double bestScore=-1;int[] winning=best;int[] countsBest=null;for(int[] t:candidates){int[] hits=new int[8];for(Draw d:history){int matches=0;for(int x:d.nums)if(contains(t,x))matches++;hits[matches]++;}double[] bonuses={0,1,4,14,55,220,900,3500};double historical=0;for(int k=0;k<=7;k++)historical+=hits[k]*bonuses[k];historical/=N;double score=preScore(t,weights)+historical*12;if(score>bestScore){bestScore=score;winning=t;countsBest=hits;}}
        ticket=winning;
        LinkedHashMap<String,Integer> tf=new LinkedHashMap<>();LinkedHashMap<String,String> display=new LinkedHashMap<>();HashMap<String,Integer> lastTeam=new HashMap<>(),r30=new HashMap<>();for(int i=0;i<N;i++){String t=history.get(i).team.trim(),key=normalize(t);if(key.isEmpty())continue;tf.put(key,tf.getOrDefault(key,0)+1);display.put(key,t);lastTeam.put(key,i);if(i>=N-30)r30.put(key,r30.getOrDefault(key,0)+1);}
        String chosen="NÃO IDENTIFICADO";double teamScore=-1;for(String k:tf.keySet()){int g=N-1-lastTeam.get(k);double s=tf.get(k)*30./N+r30.getOrDefault(k,0)*40./Math.min(30,N)+Math.min(g,100)*.3;if(s>teamScore){teamScore=s;chosen=display.get(k);}}team=chosen;
        StringBuilder sb=new StringBuilder();sb.append("TIMEMANIA CAMPEÃO V1\n\n").append("Concursos: ").append(N).append(" | Último: ").append(history.get(N-1).id).append("\n\nJOGO: ");for(int x:ticket)sb.append(String.format(java.util.Locale.US,"%02d ",x));sb.append("\nTIME: ").append(team).append("\nSCORE: ").append(String.format(java.util.Locale.US,"%.2f",bestScore));sb.append("\n\n85.000 candidatos amostrados; ").append(candidates.size()).append(" avaliados contra todo o histórico.\nRegra: exatamente 1 dezena do último sorteio.\n\nSUPERPOSIÇÃO DE FALHAS\n73 ausentes por concurso; mínimo matemático: 66.\nObservado: mínimo ").append(minOverlap).append(", máximo ").append(maxOverlap).append(", média ").append(String.format(java.util.Locale.US,"%.2f",totalOverlap/(double)(N-1))).append(".\n\nBLOCOS SEM SORTEADAS\n");for(int b=0;b<10;b++)sb.append("B").append(b+1).append(": ").append(blockMiss[b]).append(" | últimos 30: ").append(blockRecent[b]).append("\n");sb.append("\nAcertos históricos da combinação: \n");for(int k=0;k<=7;k++)sb.append(k).append(" acertos: ").append(countsBest[k]).append("\n");sb.append("\nTimes encontrados no histórico: ").append(tf.size()).append(". Confira se o time sugerido ainda consta da lista oficial vigente de 80 times da CAIXA.\n\nEstudo descritivo; não prevê sorteios futuros.");report=sb.toString();
    }
    static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(java.util.Locale.ROOT);}
    static int[] sorted(Set<Integer> s){int[] a=new int[s.size()];int i=0;for(int x:s)a[i++]=x;Arrays.sort(a);return a;}
    static boolean contains(int[] a,int x){for(int y:a)if(x==y)return true;return false;}
    static double preScore(int[] t,double[] w){boolean[] b=new boolean[10];double s=0;for(int x:t){s+=w[x];b[(x-1)/8]=true;}for(boolean x:b)if(x)s+=45;return s;}
    void writePdf(OutputStream out)throws Exception{PdfDocument doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Canvas c=page.getCanvas();Paint p=new Paint(3);p.setColor(PURPLE);c.drawRect(0,0,595,92,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(24);c.drawText("TIMEMANIA CAMPEÃO V1",32,48,p);p.setTextSize(12);c.drawText("Dezenas • Falhas • Superposição • Time do Coração",32,73,p);
        p.setColor(Color.BLACK);p.setTextSize(17);c.drawText("JOGO: "+Arrays.toString(ticket),32,130,p);p.setTextSize(13);c.drawText("TIME: "+team,32,154,p);
        for(int b=0;b<10;b++){int col=b%2,row=b/2,x=32+col*270,y=184+row*92;p.setColor(PURPLE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(x,y,x+256,y+76,8,8,p);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(12);c.drawText("BLOCO "+(b+1),x+10,y+20,p);for(int j=0;j<8;j++){int n=b*8+j+1,cx=x+17+j*30,cy=y+52;p.setColor(Color.BLACK);p.setTextSize(11);c.drawText(String.format(java.util.Locale.US,"%02d",n),cx-6,cy,p);if(contains(ticket,n)){p.setColor(GREEN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);c.drawCircle(cx,cy-4,13,p);p.setStyle(Paint.Style.FILL);}}}
        p.setColor(Color.DKGRAY);p.setTextSize(11);c.drawText("Concursos analisados: "+history.size()+" | Último: "+history.get(history.size()-1).id,32,690,p);c.drawText("73 ausentes por concurso; sobreposição mínima de 66.",32,712,p);c.drawText("Uma dezena repetida do último concurso (restrição escolhida).",32,734,p);p.setTextSize(9);c.drawText("Estudo estatístico; não aumenta a chance matemática de ganhar.",32,792,p);doc.finishPage(page);doc.writeTo(out);doc.close();}
}
