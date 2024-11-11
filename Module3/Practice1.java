public class Practice1{
    public static void main(String[] args) throws IOException {
      
      //What is the output of a?
      int a = 10;
      for(int i = 0; i < 10; i++){
          //uncomment below to see why
          System.out.println(i/2);
          if(i/2 == 0){
              a--;
          }
      }
      System.out.println("Answer:");
      System.out.println(a);
    }
  }